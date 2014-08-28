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

package de.fernflower.modules.decompiler.stats;

import java.util.ArrayList;
import java.util.List;

import de.fernflower.main.DecompilerContext;
import de.fernflower.modules.decompiler.ExprProcessor;
import de.fernflower.modules.decompiler.StatEdge;
import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.util.InterpreterUtil;


public class DoStatement extends Statement {
	
	public static final int LOOP_DO = 0;
	public static final int LOOP_DOWHILE = 1;
	public static final int LOOP_WHILE = 2;
	public static final int LOOP_FOR = 3;
	
	private int looptype;
	
	private List<Exprent> initExprent = new ArrayList<Exprent>();
	private List<Exprent> conditionExprent = new ArrayList<Exprent>();
	private List<Exprent> incExprent = new ArrayList<Exprent>();
	
	// *****************************************************************************
	// constructors
	// *****************************************************************************
	
	private DoStatement() {
		type = Statement.TYPE_DO;  
		looptype = LOOP_DO;

		initExprent.add(null);
		conditionExprent.add(null);
		incExprent.add(null);
	}
	
	private DoStatement(Statement head) {

		this();
		
		first = head;
		stats.addWithKey(first, first.id);

		// post is always null!
	}
	
	// *****************************************************************************
	// public methods
	// *****************************************************************************
	
	public static Statement isHead(Statement head) {

		if(head.getLastBasicType() == LASTBASICTYPE_GENERAL && !head.isMonitorEnter()) { 
			
			// at most one outgoing edge
			StatEdge edge = null;
			List<StatEdge> lstSuccs = head.getSuccessorEdges(STATEDGE_DIRECT_ALL);
			if(!lstSuccs.isEmpty()) {
				edge = lstSuccs.get(0); 
			}
			
			// regular loop
			if(edge!=null && edge.getType() == StatEdge.TYPE_REGULAR && edge.getDestination() == head) {
				return new DoStatement(head);
			} 
			
			// continues
			if(head.type != TYPE_DO && (edge == null || edge.getType() != StatEdge.TYPE_REGULAR) && 
					head.getContinueSet().contains(head.getBasichead())) {
				return new DoStatement(head);
			}
		}
		
		return null;
	}

	public String toJava(int indent) {
		String indstr = InterpreterUtil.getIndentString(indent);
		StringBuffer buf = new StringBuffer();

		String new_line_separator = DecompilerContext.getNewLineSeparator();
		
		buf.append(ExprProcessor.listToJava(varDefinitions, indent));
		
		if(isLabeled()) {
			buf.append(indstr+"label"+this.id+":" + new_line_separator);
		}

		switch(looptype) {
		case LOOP_DO:
			buf.append(indstr+"while(true) {" + new_line_separator);
			buf.append(ExprProcessor.jmpWrapper(first, indent+1, true));
			buf.append(indstr+"}" + new_line_separator);
			break;
		case LOOP_DOWHILE:
			buf.append(indstr+"do {" + new_line_separator);
			buf.append(ExprProcessor.jmpWrapper(first, indent+1, true));
			buf.append(indstr+"} while("+conditionExprent.get(0).toJava(indent)+");" + new_line_separator);
			break;
		case LOOP_WHILE:
			buf.append(indstr+"while("+conditionExprent.get(0).toJava(indent)+") {" + new_line_separator);
			buf.append(ExprProcessor.jmpWrapper(first, indent+1, true));
			buf.append(indstr+"}" + new_line_separator);
			break;
		case LOOP_FOR:
			buf.append(indstr+"for("+(initExprent.get(0)==null?"":initExprent.get(0).toJava(indent))+
					"; "+conditionExprent.get(0).toJava(indent)+"; "+incExprent.get(0).toJava(indent)+") {" + new_line_separator);
			buf.append(ExprProcessor.jmpWrapper(first, indent+1, true));
			buf.append(indstr+"}" + new_line_separator);
		}
		
		return buf.toString();
	}
	
	public List<Object> getSequentialObjects() {

		List<Object> lst = new ArrayList<Object>();
		
		switch(looptype) {
		case LOOP_FOR:
			if(getInitExprent() != null) {
				lst.add(getInitExprent());
			}
		case LOOP_WHILE:
			lst.add(getConditionExprent());
		}

		lst.add(first);
		
		switch(looptype) {
		case LOOP_DOWHILE:
			lst.add(getConditionExprent());
			break;
		case LOOP_FOR:
			lst.add(getIncExprent());
		}
		
		return lst;
	}
	
	public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
		if(initExprent.get(0) == oldexpr) {
			initExprent.set(0, newexpr);
		}
		if(conditionExprent.get(0) == oldexpr) {
			conditionExprent.set(0, newexpr);
		}
		if(incExprent.get(0) == oldexpr) {
			incExprent.set(0, newexpr);
		}
	}

	public Statement getSimpleCopy() {
		return new DoStatement();
	}
	
	// *****************************************************************************
	// getter and setter methods
	// *****************************************************************************
	
	public List<Exprent> getInitExprentList() {
		return initExprent;
	}
	
	public List<Exprent> getConditionExprentList() {
		return conditionExprent;
	}

	public List<Exprent> getIncExprentList() {
		return incExprent;
	}
	
	public Exprent getConditionExprent() {
		return conditionExprent.get(0);
	}

	public void setConditionExprent(Exprent conditionExprent) {
		this.conditionExprent.set(0, conditionExprent);
	}

	public Exprent getIncExprent() {
		return incExprent.get(0);
	}

	public void setIncExprent(Exprent incExprent) {
		this.incExprent.set(0, incExprent);
	}

	public Exprent getInitExprent() {
		return initExprent.get(0);
	}

	public void setInitExprent(Exprent initExprent) {
		this.initExprent.set(0, initExprent);
	}

	public int getLooptype() {
		return looptype;
	}

	public void setLooptype(int looptype) {
		this.looptype = looptype;
	}	
	
}
