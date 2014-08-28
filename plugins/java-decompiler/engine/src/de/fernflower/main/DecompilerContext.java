/*
 *    Fernflower - The Analytical Java Decompiler
 *    http://www.reversed-java.com
 *
 *    (C) 2008 - 2010, Stiver
 *
 *    This software is NEITHER public domain NOR free software 
 *    as per GNU License. See license.txt for more details.
 *
 *    This software is distributed WITHOUT ANY WARRANTY; without 
 *    even the implied warranty of MERCHANTABILITY or FITNESS FOR 
 *    A PARTICULAR PURPOSE. 
 */

package de.fernflower.main;

import java.util.HashMap;

import de.fernflower.main.collectors.CounterContainer;
import de.fernflower.main.collectors.ImportCollector;
import de.fernflower.main.collectors.VarNamesCollector;
import de.fernflower.main.extern.IFernflowerLogger;
import de.fernflower.main.extern.IFernflowerPreferences;
import de.fernflower.modules.renamer.PoolInterceptor;
import de.fernflower.struct.StructContext;


public class DecompilerContext {
	
	public static final String CURRENT_CLASS = "CURRENT_CLASS";
	public static final String CURRENT_METHOD = "CURRENT_METHOD";
	public static final String CURRENT_METHOD_DESCRIPTOR = "CURRENT_METHOD_DESCRIPTOR";
	public static final String CURRENT_VAR_PROCESSOR = "CURRENT_VAR_PROCESSOR";
	
	public static final String CURRENT_CLASSNODE = "CURRENT_CLASSNODE";
	public static final String CURRENT_METHOD_WRAPPER = "CURRENT_METHOD_WRAPPER";
	
	private static ThreadLocal<DecompilerContext> currentContext = new ThreadLocal<DecompilerContext>();
	
	private HashMap<String, Object> properties = new HashMap<String, Object>(); 

	private StructContext structcontext;
	
	private ImportCollector impcollector;
	
	private VarNamesCollector varncollector;
	
	private CounterContainer countercontainer;
	
	private ClassesProcessor classprocessor;
	
	private PoolInterceptor poolinterceptor;

	private IFernflowerLogger logger;
	
	
	private DecompilerContext(HashMap<String, Object> properties) {
		this.properties.putAll(properties);
	}

	public static void initContext(HashMap<String, Object> propertiesCustom) {

		HashMap<String, Object> mapDefault = new HashMap<String, Object>();
		
		// default settings
		mapDefault.put(IFernflowerPreferences.DECOMPILE_INNER, "1"); 
		mapDefault.put(IFernflowerPreferences.DECOMPILE_CLASS_1_4, "1"); 
		mapDefault.put(IFernflowerPreferences.DECOMPILE_ASSERTIONS, "1"); 
		mapDefault.put(IFernflowerPreferences.REMOVE_BRIDGE, "1"); 
		mapDefault.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "0"); 
		mapDefault.put(IFernflowerPreferences.HIDE_EMPTY_SUPER, "1"); 
		mapDefault.put(IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR, "1"); 
		mapDefault.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "0"); 
		mapDefault.put(IFernflowerPreferences.OUTPUT_COPYRIGHT_COMMENT, "0"); 
		mapDefault.put(IFernflowerPreferences.NO_EXCEPTIONS_RETURN, "1"); 
		mapDefault.put(IFernflowerPreferences.DECOMPILE_ENUM, "1"); 
		mapDefault.put(IFernflowerPreferences.FINALLY_DEINLINE, "1"); 
		mapDefault.put(IFernflowerPreferences.REMOVE_GETCLASS_NEW, "1"); 
		mapDefault.put(IFernflowerPreferences.LITERALS_AS_IS, "0");
		mapDefault.put(IFernflowerPreferences.ASCII_STRING_CHARACTERS, "0");
		mapDefault.put(IFernflowerPreferences.BOOLEAN_TRUE_ONE, "1");
		mapDefault.put(IFernflowerPreferences.SYNTHETIC_NOT_SET, "1"); 
		mapDefault.put(IFernflowerPreferences.UNDEFINED_PARAM_TYPE_OBJECT, "1"); 

		mapDefault.put(IFernflowerPreferences.USE_DEBUG_VARNAMES, "1"); 
		mapDefault.put(IFernflowerPreferences.MAX_PROCESSING_METHOD, "0"); 

		mapDefault.put(IFernflowerPreferences.REMOVE_EMPTY_RANGES, "1"); 
		
		mapDefault.put(IFernflowerPreferences.NEW_LINE_SEPARATOR, "0");
		mapDefault.put(IFernflowerPreferences.INDENT_STRING, "   ");

		mapDefault.put(IFernflowerPreferences.IDEA_NOT_NULL_ANNOTATION, "1");
		
		if(propertiesCustom != null) {
			mapDefault.putAll(propertiesCustom);
		}

		currentContext.set(new DecompilerContext(mapDefault));
	}
	
	public static DecompilerContext getCurrentContext() {
		return currentContext.get();
	}
	
	public static void setCurrentContext(DecompilerContext context) {
		currentContext.set(context);
	}
	
	public static Object getProperty(String key) {
		return getCurrentContext().properties.get(key);
	}
	
	public static void setProperty(String key, Object value) {
		getCurrentContext().properties.put(key, value);
	}

	public static boolean getOption(String key) {
		return "1".equals(getCurrentContext().properties.get(key));
	}
	
	public static ImportCollector getImpcollector() {
		return getCurrentContext().impcollector;
	}

	public static void setImpcollector(ImportCollector impcollector) {
		getCurrentContext().impcollector = impcollector;
	}

	public static VarNamesCollector getVarncollector() {
		return getCurrentContext().varncollector;
	}

	public static void setVarncollector(VarNamesCollector varncollector) {
		getCurrentContext().varncollector = varncollector;
	}

	public static StructContext getStructcontext() {
		return getCurrentContext().structcontext;
	}

	public static void setStructcontext(StructContext structcontext) {
		getCurrentContext().structcontext = structcontext;
	}

	public static CounterContainer getCountercontainer() {
		return getCurrentContext().countercontainer;
	}

	public static void setCountercontainer(CounterContainer countercontainer) {
		getCurrentContext().countercontainer = countercontainer;
	}

	public static ClassesProcessor getClassprocessor() {
		return getCurrentContext().classprocessor;
	}

	public static void setClassprocessor(ClassesProcessor classprocessor) {
		getCurrentContext().classprocessor = classprocessor;
	}

	public static PoolInterceptor getPoolInterceptor() {
		return getCurrentContext().poolinterceptor;
	}

	public static void setPoolInterceptor(PoolInterceptor poolinterceptor) {
		getCurrentContext().poolinterceptor = poolinterceptor;
	}

	public static IFernflowerLogger getLogger() {
		return getCurrentContext().logger;
	}

	public static void setLogger(IFernflowerLogger logger) {
		getCurrentContext().logger = logger;
		setLogSeverity();
	}
	
	private static void setLogSeverity() {
		IFernflowerLogger logger = getCurrentContext().logger; 
		
		if(logger != null) {
			String severity = (String)getProperty(IFernflowerPreferences.LOG_LEVEL);
			if(severity != null) {
				Integer iSeverity = IFernflowerLogger.mapLogLevel.get(severity.toUpperCase());
				if(iSeverity != null) {
					logger.setSeverity(iSeverity);
				}
			}
		}
	}
	
	public static String getNewLineSeparator() {
		return getOption(IFernflowerPreferences.NEW_LINE_SEPARATOR) ?
               IFernflowerPreferences.LINE_SEPARATOR_LIN : IFernflowerPreferences.LINE_SEPARATOR_WIN ;
	}
}
