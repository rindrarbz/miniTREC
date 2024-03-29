package org.apache.lucene.demo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.demo.SpanishAnalyzer2;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

public class SearchFiles {
	private SearchFiles() {}

	  public static void main(String[] args) throws Exception {

	    String index = "index";
	    String field = "contents";
	    String infoNeeds = null;
	    String output = null;
	    String queries = null;
	    	    
	    String queryString = null;
	    int maxHits = 45; 
	    
	    /** Parse all the parameters */
	    for(int i = 0;i < args.length;i++) {
	      if ("-index".equals(args[i])) {
	        index = args[i+1];
	        i++;
	      } else if ("-infoNeeds".equals(args[i])) {
	    	infoNeeds = args[i+1];
	    	i++;
	      } else if ("-output".equals(args[i])) {
	    	  output = args[i+1];
	    	  i++;
	      }
	    }
	    
	    if (infoNeeds == null) {
	    	System.err.println("Must provide infoNeeds file with -infoNeeds");
		    System.exit(1);
	    }
	    
	    if (output == null) {
	    	System.err.println("Must provide output file with -output");
		    System.exit(1);
	    }
	    
	    /** PARSING XML FILE INFONEEDS */
	    System.out.println("PARSING XML INFONEEDS");
	    
	    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        // Load the input XML document, parse it and return an instance of the
        // Document class.
        org.w3c.dom.Document document = builder.parse(new File(infoNeeds));
        NodeList nodeList = document.getDocumentElement().getChildNodes();
        
        ArrayList<ObjectQuery> informationNeeds = new ArrayList<ObjectQuery> ();
        
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);

            if (node.getNodeType() == Node.ELEMENT_NODE) {
                 Element elem = (Element) node;
                 String identifier = elem.getElementsByTagName("identifier")
                                     .item(0).getChildNodes().item(0).getNodeValue();
                 String text = elem.getElementsByTagName("text").item(0)
                                     .getChildNodes().item(0).getNodeValue();
                 System.out.println("ID: " + identifier + ", text: " + text);
                 informationNeeds.add(new ObjectQuery(identifier, text));
            }
        }
        
	    
	    IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
	    IndexSearcher searcher = new IndexSearcher(reader);
	    Analyzer analyzer = new SpanishAnalyzer2();

	    QueryParser parser = new QueryParser(field, analyzer);
	    
	    ArrayList<ArrayList<Results>> results = new ArrayList<ArrayList<Results>>();
	    
	    for (ObjectQuery obj_q: informationNeeds) {
	    	
	    	String text_query = obj_q.text_query.toLowerCase();
	    	text_query = Normalizer.normalize(text_query, Normalizer.Form.NFD);
	    	text_query = text_query.replaceAll("[^\\p{ASCII}]", "");
	    	
	    	String lucene_query = ""; 
	    	if (text_query.contains("tesis") & (!text_query.contains("academico")
	    			| text_query.contains("fin de grado"))) {
	    		
	    		lucene_query += "type:TESIS ";
	    	}
	    	if ((text_query.contains("academico") | text_query.contains("fin de grado"))
	    			& (!text_query.contains("tesis"))) {
	    		
	    		lucene_query += "type:TAZ* ";
	    	}
	    	
	    	LinkedList<String> numbers = new LinkedList<String>();
	    	
	    	Pattern p = Pattern.compile("\\d+");
	    	Matcher m = p.matcher(text_query);
	    	
	    	while(m.find()) {
	    		numbers.add(m.group());
	    	}
	    	
	    	if (numbers.size() == 2) {
	    		lucene_query += "date:[" + numbers.get(0) + " TO " + numbers.get(1) + "] ";
	    	}
	    		    	
	    	String[] text_list = text_query.split("\\s+|(?=\\p{Punct})|(?<=\\p{Punct})");
	    	
	    	CharArraySet stopSet = SpanishAnalyzer.getDefaultStopSet();
	    	
	    	    	
	    	for (String word: text_list) {
	    		if (word.matches("[a-z]+") && !stopSet.contains(word)) {
	    			lucene_query += "subject:" + word + " ";
	    			lucene_query += "title:" + word + " ";
	    		}
	    	}
	    		    	
	    	//System.out.println(lucene_query);
	    	
	    	Query q = parser.parse(lucene_query);
	    	obj_q.setQuery(q);
	    	
	    	results.add(doSearch(searcher, obj_q, maxHits, queries == null && queryString == null));
	    }
	    
	    /** Write on the result file all the results */
	    FileWriter writer = new FileWriter(output);
	    for (ArrayList<Results> res: results) {
	    	for (Results r: res) {
	    		Path p = Paths.get(r.id_doc);
	    		String doc = p.getFileName().toString();
	    		String file_result = r.id_query + "\t" 
	    				+ doc;
	    		writer.write(file_result);
	    		writer.write("\n");
	    	}
	    	
	    }
	    writer.close();
	  }

	  public static ArrayList<Results> doSearch(IndexSearcher searcher, ObjectQuery obj_query, 
	                                     int maxHits, boolean interactive) throws IOException {

		  Query query = obj_query.query;
		  TopDocs results = searcher.search(query, maxHits);
		  ScoreDoc[] hits = results.scoreDocs;
		  
		  int end = Math.min(hits.length, maxHits);
		  //int numTotalHits = Math.toIntExact(results.totalHits.value);
		  //System.out.println(numTotalHits + " total matching documents");
	    
		  ArrayList<Results> results_query = new ArrayList<Results>();
	    
		  for (int i=0; i<end; i++) {

			  Document doc = searcher.doc(hits[i].doc);
		      String path = doc.get("path");
		      if (path != null) {
		    	  System.out.println((i+1) + ". " + path);
		    	  Results r = new Results(obj_query.id_query, path);   
		    	  results_query.add(r);
		      }
	        
		      /** Explaining results */
		      //System.out.println(searcher.explain(query, hits[i].doc));
	                  
	      }
	  
	  return results_query;
	  
	  }
}
