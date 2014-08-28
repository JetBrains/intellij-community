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

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.SequenceHelper;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.util.InterpreterUtil;


public class SynchronizedStatement extends Statement {

	private Statement body;
	
	private List<Exprent> headexprent = new ArrayList<Exprent>();
	
	// *****************************************************************************
	// constructors
	// *****************************************************************************
	
	public SynchronizedStatement() {
		type = TYPE_SYNCRONIZED;

		headexprent.add(null);
	}
	
	public SynchronizedStatement(Statement head, Statement body, Statement exc) {
		
		this();  

		first = head;
		stats.addWithKey(head, head.id);

		this.body = body; 
		stats.addWithKey(body, body.id);
		
		stats.addWithKey(exc, exc.id);
		
		List<StatEdge> lstSuccs = body.getSuccessorEdges(STATEDGE_DIRECT_ALL);
		if(!lstSuccs.isEmpty()) {
			StatEdge edge = lstSuccs.get(0);
			if(edge.getType() == StatEdge.TYPE_REGULAR) {
				post = edge.getDestination();
			}
		}
		
	}
	
	
	// *****************************************************************************
	// public methods
	// *****************************************************************************
	
	public String toJava(int indent) {
		String indstr = InterpreterUtil.getIndentString(indent);
		
		String new_line_separator = DecompilerContext.getNewLineSeparator();
		
		StringBuffer buf = new StringBuffer();
		buf.append(ExprProcessor.listToJava(varDefinitions, indent));
		buf.append(first.toJava(indent));
		
		if(isLabeled()) {
			buf.append(indstr+"label"+this.id+":" + new_line_separator);
		}
		
		buf.append(indstr+headexprent.get(0).toJava(indent)+" {" + new_line_separator);
		buf.append(ExprProcessor.jmpWrapper(body, indent+1, true));
		buf.append(indstr+"}" + new_line_separator);
		
		return buf.toString();
	}
	
	public void initExprents() {
		headexprent.set(0, first.getExprents().remove(first.getExprents().size()-1));
	}
	
	public List<Object> getSequentialObjects() {

		List<Object> lst = new ArrayList<Object>(stats);
		lst.add(1, headexprent.get(0));
		
		return lst;
	}
	
	public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
		if(headexprent.get(0) == oldexpr) {
			headexprent.set(0, newexpr);
		}
	}
	
	public void replaceStatement(Statement oldstat, Statement newstat) {

		if(body == oldstat) {
			body = newstat;
		}

		super.replaceStatement(oldstat, newstat);
	}
	
	public void removeExc() {
		Statement exc = stats.get(2);
		SequenceHelper.destroyStatementContent(exc, true);
		
		stats.removeWithKey(exc.id);
	}
	
	public Statement getSimpleCopy() {
		return new SynchronizedStatement();
	}
	
	public void initSimpleCopy() {		
		first = stats.get(0);
		body = stats.get(1);
	}
	
	// *****************************************************************************
	// getter and setter methods
	// *****************************************************************************
	
	public Statement getBody() {
		return body;
	}

	public void setBody(Statement body) {
		this.body = body;
	}

	public List<Exprent> getHeadexprentList() {
		return headexprent;
	}
	
	public Exprent getHeadexprent() {
		return headexprent.get(0);
	}
	
}
