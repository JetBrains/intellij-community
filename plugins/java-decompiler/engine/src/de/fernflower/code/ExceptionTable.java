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

import java.util.ArrayList;
import java.util.List;

import de.fernflower.code.interpreter.Util;
import de.fernflower.struct.StructContext;

public class ExceptionTable {

	private List<ExceptionHandler> handlers = new ArrayList<ExceptionHandler>();
	
	public ExceptionTable() {}
	
	public ExceptionTable(List<ExceptionHandler> handlers) {
		this.handlers = handlers;
	}
	
	
	public ExceptionHandler getHandlerByClass(StructContext context, int line, String valclass, boolean withany) {
		
		ExceptionHandler res = null; // no handler found
		
		for(ExceptionHandler handler : handlers) {
			if(handler.from<=line && handler.to>line) {
				String name = handler.exceptionClass;
				
				if((withany && name==null) ||   // any -> finally or synchronized handler
						(name!=null && Util.instanceOf(context, valclass, name))) { 
					res = handler;
					break;
				} 
			}
		}
		
		return res;
	}
	
	public List<ExceptionHandler> getHandlers() {
		return handlers;
	}
	
}
