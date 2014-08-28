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

package org.jetbrains.java.decompiler.main.decompiler.helper;

import java.io.PrintStream;

import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

public class PrintStreamLogger implements IFernflowerLogger {

	private int severity;
	
	private int indent; 
	
	private PrintStream stream;
	
	public PrintStreamLogger(int severity, PrintStream stream) {
		this.severity = severity;
		this.indent = 0; 
		this.stream = stream;
	}
	
	
	public void writeMessage(String message, int severity) {
		if (severity >= this.severity) {
			stream.println(InterpreterUtil.getIndentString(indent) + names[severity] + ": " + message);
		}
	}

	public void writeMessage(String message, Throwable t) {
		t.printStackTrace(stream);
		writeMessage(message, ERROR);
	}

	public void startClass(String classname) {
		stream.println(InterpreterUtil.getIndentString(indent++)+"Processing class "+classname+" ...");
	}

	public void endClass() {
		stream.println(InterpreterUtil.getIndentString(--indent)+"... proceeded.");
	}

	public void startWriteClass(String classname) {
		stream.println(InterpreterUtil.getIndentString(indent++)+"Writing class "+classname+" ...");
	}

	public void endWriteClass() {
		stream.println(InterpreterUtil.getIndentString(--indent)+"... written.");
	}
	
	public void startMethod(String method) {
		if(severity <= INFO) {
			stream.println(InterpreterUtil.getIndentString(indent)+"Processing method "+method+" ...");
		}
	}

	public void endMethod() {
		if(severity <= INFO) {
			stream.println(InterpreterUtil.getIndentString(indent)+"... proceeded.");
		}
	}

	public int getSeverity() {
		return severity;
	}

	public void setSeverity(int severity) {
		this.severity = severity;
	}
}
