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

package de.fernflower.main.extern;

import java.util.HashMap;

public interface IFernflowerLogger {

	public static final int TRACE = 1;
	public static final int INFO = 2;
	public static final int WARNING = 3;
	public static final int ERROR = 4;
	public static final int IMMEDIATE = 5;

	public static final HashMap<String, Integer> mapLogLevel = new HashMap<String, Integer>() {{
		put("TRACE", 1);
		put("INFO", 2);
		put("WARN", 3);
		put("ERROR", 4);
		put("IMME", 5);
	}};
	
	public static final String[] names = new String[] {""/*DUMMY ENTRY*/, "TRACE", "INFO", "WARNING", "ERROR", ""/*IMMEDIATE*/};
	
	public void writeMessage(String message, int severity);

	public void writeMessage(String message, Throwable t);
	
	public void startClass(String classname);

	public void endClass();

	public void startWriteClass(String classname);

	public void endWriteClass();
	
	public void startMethod(String method);

	public void endMethod();
	
	public int getSeverity();

	public void setSeverity(int severity);
}
