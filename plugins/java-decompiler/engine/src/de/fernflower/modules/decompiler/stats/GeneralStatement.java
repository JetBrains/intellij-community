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

package org.jetbrains.java.decompiler.modules.decompiler.stats;

import java.util.Collection;
import java.util.HashSet;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.util.InterpreterUtil;



public class GeneralStatement extends Statement {
	
	// *****************************************************************************
	// constructors
	// *****************************************************************************
	
	private GeneralStatement() {
		type = Statement.TYPE_GENERAL;
	}
	
	public GeneralStatement(Statement head, Collection<Statement> statements, Statement post) {
		
		this();
		
		first = head;
		stats.addWithKey(head, head.id);

		HashSet<Statement> set = new HashSet<Statement>(statements);
		set.remove(head);
		
		for(Statement st : set) {
			stats.addWithKey(st, st.id);
		}

		this.post = post;
	}
	
	// *****************************************************************************
	// public methods
	// *****************************************************************************
	
	public String toJava(int indent) {
		String indstr = InterpreterUtil.getIndentString(indent);
		StringBuffer buf = new StringBuffer();
		
		String new_line_separator = DecompilerContext.getNewLineSeparator();
		
		if(isLabeled()) {
			buf.append(indstr+"label"+this.id+":" + new_line_separator);
		}
		
		buf.append(indstr+"abstract statement {" + new_line_separator);
		for(int i=0;i<stats.size();i++) {
			buf.append(stats.get(i).toJava(indent+1));
		}
		buf.append(indstr+"}");

		return buf.toString(); 
	}
	
}
