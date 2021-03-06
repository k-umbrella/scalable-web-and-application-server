package edu.upenn.cis455.webserver;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.servlet.http.HttpServlet;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.FileAppender;
import org.apache.log4j.HTMLLayout;
import org.apache.log4j.Logger;
import org.xml.sax.SAXException;
//### error log  ### readme refering the dir?

/**
 * This class is a center station.        ------------   wait() ----------- 
 * 1. It creates new thread pool;   Task->|Task Queue| <--------| ) ) ) ) | 
 * 2. Accepts new client requests;        ------------          -----------
 * 3. Add task to queue.							            Thread Pool
 * @author Yayang Tian
 */
public class Dispatcher {
	static ThreadPool pool;
	static ServerSocket serverSocket;
	static TaskQueue taskQueue;
	static boolean shutdown = false;
	static String rootDir;
	static String webxml;
	
	static HashMap<String, HttpServlet> servletContainer;
	static ServiceHandler handler;
	static ArrayList<FakeSession> sessionList;
	static int sessionTimeout;
	
	static Logger logger = Logger.getLogger(Dispatcher.class);
	FileAppender fileAppender;
	static boolean dumpDetail = false;

	public Dispatcher(int port, String rootDir, String xml) throws IOException{
		serverSocket = new ServerSocket(port);
		taskQueue = new TaskQueue(rootDir);
		pool = new ThreadPool(10);
		webxml = xml;
		fileAppender = new FileAppender(new HTMLLayout(),"ErrorLog.html");
		logger.addAppender(fileAppender);
		System.out.println("Server started. Waiting for Http request...\n");

	}

	/**
	 *  Initialization of Handler, Servlet and Servlet Context 
	 */
	public void setupService() throws Exception{
//		logger.debug("setup servlet");
		handler = parseXML(webxml);
		FakeContext context = createContext(handler);
		servletContainer = createServlets(handler, context);
		sessionList =  new ArrayList<FakeSession>();
//		dumpServlets(handler);
	}


	/**
	 * HTTP request/response interface
	 */
	public void listenClient() throws IOException{
		while(!shutdown){
			try{
				Socket clientSocket = serverSocket.accept();
 
				// add all accepted client tasks to queue
				boolean success = taskQueue.add(clientSocket);

				// if fail due to overload, print wrong msg
				if(!success){
					processOverload(clientSocket);
				}
			} catch(SocketTimeoutException e){
				logger.error("timeout");
				continue;
				
			} 
		}
	}


	/**
	 * Parsed config file
	 */
	private static ServiceHandler parseXML(String xml) throws SAXException, IOException, ParserConfigurationException{
		ServiceHandler handler = new ServiceHandler();
		File xmlFile = new File(xml);
		if(!xmlFile.exists()){
			System.err.println("[Error] Could not read xml file.");
			System.exit(-1);
		}
		SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
		parser.parse(xmlFile, handler);
		return handler;
	}

	/**
	 * Create a context object 
	 * @author Todd J. Green & Nick Taylor, modifed by Yayang Tian
	 */
	private static FakeContext createContext(ServiceHandler handler){
		FakeContext context = new FakeContext(handler);
		for(String name : handler.m_contextParams.keySet()){
			context.setInitParam(name, handler.m_contextParams.get(name));
		}
		context.setAttribute("session-timeout", handler.m_sessionTimeout);
		context.setAttribute("urlMappings", handler.m_urlMappings);
		return context;
	}

	/**
	 * Creates some servlets from context info and return the mapping  
	 * @author Todd J. Green & Nick Taylor, modified by Yayang Tian
	 */
	private static HashMap<String,HttpServlet> createServlets(ServiceHandler handler, FakeContext fc) throws Exception {
		HashMap<String,HttpServlet> servlets = new HashMap<String,HttpServlet>();

		for (String servletName : handler.m_servlets.keySet()) {
			FakeConfig config = new FakeConfig(servletName, fc);
			String className = handler.m_servlets.get(servletName);
			// Dynamic loading
			@SuppressWarnings("rawtypes")
			Class servletClass = Class.forName(className);
			// A new baby instance is comming for every servlet defined in xml file!!!
			HttpServlet servlet = (HttpServlet) servletClass.newInstance();
			HashMap<String,String> servletParams = handler.m_servletParams.get(servletName);
			if (servletParams != null) {
				for (String param : servletParams.keySet()) {
					config.setInitParam(param, servletParams.get(param));
				}
			}
			servlet.init(config);
			servlets.put(servletName, servlet);
		}
		return servlets;
	}




	/**
	 * Auxiliary functions
	 */
	public static void dumpServlets(ServiceHandler handler){
//		dump("m_servletName", handler.m_servletName);
//		dump("m_paramName", handler.m_paramName);
//		dump("m_state", handler.m_state);
//		dump("m_appName", handler.m_appName);

		dump("m_servlets");
		for(String name : handler.m_servlets.keySet()){
			dump(name, handler.m_servlets.get(name));
		}
		dump("m_contextParams:");
		for(String name : handler.m_contextParams.keySet()){
			dump(name, handler.m_contextParams.get(name));
		}
		dump("m_servletParams:");
		for(String name : handler.m_servletParams.keySet()){
			dump(name, "");
			HashMap<String, String> paramSet = handler.m_servletParams.get(name);
			for(String paramId : paramSet.keySet()){
				dump(paramId, handler.m_contextParams.get(name));
			}
		}
	}
	// URL stuff
	public static HashMap<String, String> getUrlMappings(){
		return handler.m_urlMappings;
	}
	public static HttpServlet getServlet(String name){
		return servletContainer.get(name);
	}
	
	// Session Macro Control
	public static void addSession(FakeSession fs){
		
		System.out.println("Hey!" + "Befre!");
		sessionList.add(fs);
		System.out.println("Hey!" + "After!");
		System.out.println("Hey! ID is" + fs.getId());
		
	}
	public static FakeSession getSession(String id){
		for(FakeSession fs : sessionList){
			if(fs.isValid()) 
				fs.getId().equals(id);
			return fs;
		}
		return null;
	}
	public static ArrayList<FakeSession> getSessionList(){
		return sessionList;
	}
	
	public static ArrayList<FakeSession> getSessions(){
		return sessionList;
	}
	public static int getSessionTimeout(int sid){
		FakeSession ss = sessionList.get(sid);
		return ss.getMaxInactiveInterval();
	}
	public static void removeSession(FakeSession session){
		System.out.println("[!!!debug]\t" + session.isValid());
				sessionList.remove(session);
				System.out.println("[!!contains]\t" + sessionList.contains(session));
		
	}
	
	public static boolean isSessionValid(String sid){
		return sessionList.contains(sid);
	}
	public static void closeServer(){
		try {
			shutdown = true;
			pool.stopAll();
			serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void processOverload(Socket clientSocket) throws IOException{
		PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
		pw.write("HTTP/1.0 503 Server Unavailable\r\nConnection:Closed\r\n\r\n");
		pw.write("Not success. This is the body !\r\n");
		System.out.println("HTTP/1.0 503 Server Unavaliable\n" +
				"Connection: Close\n\n");
		pw.close();
	}

	

	public static boolean isShutdown(){
		return shutdown;
	}

	public static TaskQueue getTaskQueue(){
		return taskQueue;
	}

	public static ServerSocket getServerSocket(){
		return serverSocket;
	}

	public static ArrayList<Worker> getWorkers(){
		return pool.getWorkers();
	}

	public static String getRootDir(){
		return rootDir;
	}

	public synchronized void addTask(Socket task){
		if(!pool.shutdown()){
			taskQueue.add(task);
		}
		else throw new IllegalStateException
		("You cannot add task because thread pool is stopped.");
	}

	private static void dump(String note, Object item){
		if(dumpDetail == true)
			System.out.println(note + ":\t\t" + String.valueOf(item));
	}
	private static void dump(Object item){
		System.out.println("\n/*** "+ String.valueOf(item) + "***/");
	}

}
