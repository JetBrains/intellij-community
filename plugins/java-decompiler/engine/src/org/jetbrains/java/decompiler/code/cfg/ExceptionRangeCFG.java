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

package org.jetbrains.java.decompiler.code.cfg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.java.decompiler.main.DecompilerContext;

public class ExceptionRangeCFG {
	
	private List<BasicBlock> protectedRange = new ArrayList<BasicBlock>(); // FIXME: replace with set 
	
	private BasicBlock handler;
	
	private List<String> exceptionTypes;
	
	public ExceptionRangeCFG(List<BasicBlock> protectedRange, BasicBlock handler, List<String> exceptionType) {
		this.protectedRange = protectedRange;
		this.handler = handler;
		
		if(exceptionType != null) {
			this.exceptionTypes = new ArrayList<String>(exceptionType);
		}
	}

	public boolean isCircular() {
		return protectedRange.contains(handler); 
	}
	
	public String toString() {
		
		String new_line_separator = DecompilerContext.getNewLineSeparator();
		
		StringBuffer buf = new StringBuffer(); 

		buf.append("exceptionType:");
		for(String exception_type : exceptionTypes) {
			buf.append(" "+exception_type);
		}
		buf.append(new_line_separator);

		buf.append("handler: "+handler.id+new_line_separator);
		buf.append("range: ");
		for(int i=0;i<protectedRange.size();i++) {
			buf.append(protectedRange.get(i).id+" ");
		}
		buf.append(new_line_separator);
		
		return buf.toString(); 
	}
	
	public BasicBlock getHandler() {
		return handler;
	}

	public void setHandler(BasicBlock handler) {
		this.handler = handler;
	}

	public List<BasicBlock> getProtectedRange() {
		return protectedRange;
	}

	public void setProtectedRange(List<BasicBlock> protectedRange) {
		this.protectedRange = protectedRange;
	}

	public List<String> getExceptionTypes() {
		return this.exceptionTypes;
	}

	public void addExceptionType(String exceptionType) {
		
		if(this.exceptionTypes == null) {
			return;
		}
		
		if(exceptionType == null) {
			this.exceptionTypes = null;
		} else {
			this.exceptionTypes.add(exceptionType);
		}
	}
	
	public String getUniqueExceptionsString() {
	
		if(exceptionTypes == null) {
			return null;
		}
		
		Set<String> setExceptionStrings = new HashSet<String>();
		
		for(String exceptionType : exceptionTypes) { // normalize order
			setExceptionStrings.add(exceptionType);
		}

		String ret = "";
		for(String exception : setExceptionStrings) {
			if(!ret.isEmpty()) {
				ret += ":";
			}
			ret += exception;
		}

		return ret;
	}
	
	
//	public void setExceptionType(String exceptionType) {
//		this.exceptionType = exceptionType;
//	}
	
}
