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

package de.fernflower.code;

import java.io.DataOutputStream;
import java.io.IOException;

import de.fernflower.main.DecompilerContext;

public class ExceptionHandler {

	public int from = 0;
	public int to = 0;
	public int handler = 0;

	public int from_instr = 0;
	public int to_instr = 0;
	public int handler_instr = 0;
	
	public int class_index = 0;
	public String exceptionClass = null;
	
	public ExceptionHandler(){}
	
	public ExceptionHandler(int from_raw, int to_raw, int handler_raw, String exceptionClass) {
		this.from = from_raw;
		this.to = to_raw;
		this.handler = handler_raw;
		this.exceptionClass = exceptionClass;
	}
	
	public void writeToStream(DataOutputStream out) throws IOException {
		out.writeShort(from);
		out.writeShort(to);
		out.writeShort(handler);
		out.writeShort(class_index);
	}
	
	public String toString() {
		
		String new_line_separator = DecompilerContext.getNewLineSeparator();
		
		StringBuffer buf = new StringBuffer();
		buf.append("from: "+from+" to: "+to+" handler: "+handler+new_line_separator);
		buf.append("from_instr: "+from_instr+" to_instr: "+to_instr+" handler_instr: "+handler_instr+new_line_separator);
		buf.append("exceptionClass: "+exceptionClass+new_line_separator);
		return buf.toString(); 
	}
	
}
