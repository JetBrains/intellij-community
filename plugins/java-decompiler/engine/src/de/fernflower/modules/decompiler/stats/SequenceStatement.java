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

import java.util.Arrays;
import java.util.List;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.decompiler.DecHelper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.util.InterpreterUtil;


public class SequenceStatement extends Statement {

	
	// *****************************************************************************
	// constructors
	// *****************************************************************************
	
	private SequenceStatement() {
		type = Statement.TYPE_SEQUENCE;  
	}
	
	public SequenceStatement(List<Statement> lst) {

		this();
		
		lastBasicType = lst.get(lst.size()-1).getLastBasicType();
		
		for(Statement st: lst) {
			stats.addWithKey(st, st.id);
		}
		
		first = stats.get(0);
	}

	private SequenceStatement(Statement head, Statement tail) {

		this(Arrays.asList(new Statement[] {head, tail}));
		
		List<StatEdge> lstSuccs = tail.getSuccessorEdges(STATEDGE_DIRECT_ALL);
		if(!lstSuccs.isEmpty()) {
			StatEdge edge = lstSuccs.get(0);
			
			if(edge.getType() == StatEdge.TYPE_REGULAR && edge.getDestination() != head) {
				post = edge.getDestination();
			}
		}
	}
	
	
	// *****************************************************************************
	// public methods
	// *****************************************************************************
	
	public static Statement isHead2Block(Statement head) {
		
		if(head.getLastBasicType() != Statement.LASTBASICTYPE_GENERAL) {
			return null;
		}
		
		// at most one outgoing edge
		StatEdge edge = null;
		List<StatEdge> lstSuccs = head.getSuccessorEdges(STATEDGE_DIRECT_ALL);
		if(!lstSuccs.isEmpty()) {
			edge = lstSuccs.get(0); 
		}
		
		if(edge != null && edge.getType() == StatEdge.TYPE_REGULAR) {
			Statement stat = edge.getDestination(); 
			
			if(stat != head && stat.getPredecessorEdges(StatEdge.TYPE_REGULAR).size() == 1
					&& !stat.isMonitorEnter()) {

				if(stat.getLastBasicType() == Statement.LASTBASICTYPE_GENERAL) {
					if(DecHelper.checkStatementExceptions(Arrays.asList(new Statement[] {head, stat}))) {
						return new SequenceStatement(head, stat); 
					}
				}
			}
		}
		
		return null;
	}
	
	public String toJava(int indent) {
		
		StringBuilder buf = new StringBuilder();
		
		String indstr = null;
		boolean islabeled = isLabeled(); 

		String new_line_separator = DecompilerContext.getNewLineSeparator();
		
		buf.append(ExprProcessor.listToJava(varDefinitions, indent));

		if(islabeled) {
			indstr = InterpreterUtil.getIndentString(indent);
			indent++;
			buf.append(indstr+"label"+this.id+": {" + new_line_separator);
		}

		boolean notempty = false; 
		
		for(int i=0;i<stats.size();i++) {
			
			Statement st = stats.get(i);
			
			if(i>0 && notempty) {
				buf.append(new_line_separator);
			}
			
			String str = ExprProcessor.jmpWrapper(st, indent, false);
			buf.append(str);
			
			notempty = (str.trim().length() > 0);
		}
		
		if(islabeled) {
			buf.append(indstr+"}" + new_line_separator);
		}

		return buf.toString();
	}

	public Statement getSimpleCopy() {
		return new SequenceStatement();
	}
	
}
