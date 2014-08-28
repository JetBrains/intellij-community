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

package org.jetbrains.java.decompiler.main.extern;

public interface IFernflowerPreferences {

	public static final String REMOVE_BRIDGE = "rbr";
	public static final String REMOVE_SYNTHETIC = "rsy"; 	
	public static final String DECOMPILE_INNER = "din";  	
	public static final String DECOMPILE_CLASS_1_4 = "dc4"; 	
	public static final String DECOMPILE_ASSERTIONS = "das"; 	
	public static final String HIDE_EMPTY_SUPER = "hes";			
	public static final String HIDE_DEFAULT_CONSTRUCTOR = "hdc";	 
	public static final String DECOMPILE_GENERIC_SIGNATURES = "dgs"; 
	public static final String OUTPUT_COPYRIGHT_COMMENT = "occ";	
	public static final String NO_EXCEPTIONS_RETURN = "ner";			
	public static final String DECOMPILE_ENUM = "den";						
	public static final String REMOVE_GETCLASS_NEW = "rgn";				
	public static final String LITERALS_AS_IS = "lit";
	public static final String BOOLEAN_TRUE_ONE = "bto";
	public static final String SYNTHETIC_NOT_SET = "nns";		
	public static final String UNDEFINED_PARAM_TYPE_OBJECT = "uto";
	public static final String USE_DEBUG_VARNAMES = "udv";
	public static final String MAX_PROCESSING_METHOD = "mpm";
	public static final String REMOVE_EMPTY_RANGES = "rer";			
	public static final String ASCII_STRING_CHARACTERS = "asc";			
	
	public static final String FINALLY_DEINLINE = "fdi";

	public static final String FINALLY_CATCHALL = "FINALLY_CATCHALL";
	public static final String FINALLY_SEMAPHOR = "FINALLY_SEMAPHOR";

	public static final String RENAME_ENTITIES = "ren";			
	public static final String USER_RENAMER_CLASS = "urc";			
	
	public static final String LOG_LEVEL = "log";	
	
	public static final String NEW_LINE_SEPARATOR = "nls";
	public static final String IDEA_NOT_NULL_ANNOTATION = "inn";
	public static final String LAMBDA_TO_ANONYMOUS_CLASS = "lac";
  public static final String INDENT_STRING = "ind";

	public static final String LINE_SEPARATOR_WIN = "\r\n";
	public static final String LINE_SEPARATOR_LIN = "\n";
}
