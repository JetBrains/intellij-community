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
import java.util.HashSet;
import java.util.List;

import de.fernflower.code.CodeConstants;
import de.fernflower.main.DecompilerContext;
import de.fernflower.main.collectors.CounterContainer;
import de.fernflower.modules.decompiler.DecHelper;
import de.fernflower.modules.decompiler.ExprProcessor;
import de.fernflower.modules.decompiler.StatEdge;
import de.fernflower.modules.decompiler.exps.VarExprent;
import de.fernflower.modules.decompiler.vars.VarProcessor;
import de.fernflower.struct.gen.VarType;
import de.fernflower.util.InterpreterUtil;

public class CatchStatement extends Statement {
	
	private List<List<String>> exctstrings = new ArrayList<List<String>>();  

	private List<VarExprent> vars = new ArrayList<VarExprent>();  
	
	// *****************************************************************************
	// constructors
	// *****************************************************************************
	
	private CatchStatement() {
		type = TYPE_TRYCATCH;  
	}
	
	private CatchStatement(Statement head, Statement next, HashSet<Statement> setHandlers) {
		
		this();
		
		first = head;
		stats.addWithKey(first, first.id);

		for(StatEdge edge : head.getSuccessorEdges(StatEdge.TYPE_EXCEPTION)) {
			Statement stat = edge.getDestination();
			
			if(setHandlers.contains(stat)) {
				stats.addWithKey(stat, stat.id);
				exctstrings.add(new ArrayList<String>(edge.getExceptions()));
				
				vars.add(new VarExprent(DecompilerContext.getCountercontainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER), 
						new VarType(CodeConstants.TYPE_OBJECT, 0, edge.getExceptions().get(0)), // FIXME: for now simply the first type. Should get the first common superclass when possible. 
						(VarProcessor)DecompilerContext.getProperty(DecompilerContext.CURRENT_VAR_PROCESSOR)));
			}
		}
		
		if(next != null) {
			post = next;
		}
		
	}

	// *****************************************************************************
	// public methods
	// *****************************************************************************
	
	public static Statement isHead(Statement head) {

		if(head.getLastBasicType() != LASTBASICTYPE_GENERAL) {
			return null;
		}
		
		HashSet<Statement> setHandlers = DecHelper.getUniquePredExceptions(head); 
		
		if(!setHandlers.isEmpty()) {

			int hnextcount = 0; // either no statements with connection to next, or more than 1
			
			Statement next = null;
			List<StatEdge> lstHeadSuccs = head.getSuccessorEdges(STATEDGE_DIRECT_ALL);
			if(!lstHeadSuccs.isEmpty() && lstHeadSuccs.get(0).getType() == StatEdge.TYPE_REGULAR) {
				next = lstHeadSuccs.get(0).getDestination();
				hnextcount = 2;
			}
			
			for(StatEdge edge : head.getSuccessorEdges(StatEdge.TYPE_EXCEPTION)) {
				Statement stat = edge.getDestination();
				
				boolean handlerok = true; 
				
				if(edge.getExceptions() != null && setHandlers.contains(stat)) {
					if(stat.getLastBasicType() != LASTBASICTYPE_GENERAL) {
						handlerok = false;
					} else { 
						List<StatEdge> lstStatSuccs = stat.getSuccessorEdges(STATEDGE_DIRECT_ALL);
						if(!lstStatSuccs.isEmpty() && lstStatSuccs.get(0).getType() == StatEdge.TYPE_REGULAR) {

							Statement statn = lstStatSuccs.get(0).getDestination(); 

							if(next == null) {
								next = statn;
							} else if(next != statn) {
								handlerok = false;
							}

							if(handlerok) {
								hnextcount++;
							}
						}
					}
				} else {
					handlerok = false;
				}
				
				if(!handlerok) {
					setHandlers.remove(stat);
				}
			}
			
			if(hnextcount != 1 && !setHandlers.isEmpty()) {
				List<Statement> lst = new ArrayList<Statement>();
				lst.add(head);
				lst.addAll(setHandlers);
				
				for(Statement st : lst) {
					if(st.isMonitorEnter()) {
						return null;
					}
				}
				
				if(DecHelper.checkStatementExceptions(lst)) {
					return new CatchStatement(head, next, setHandlers);
				}
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
		
		buf.append(indstr+"try {" + new_line_separator);
		buf.append(ExprProcessor.jmpWrapper(first, indent+1, true));
		buf.append(indstr+"}");
		
		for(int i=1;i<stats.size();i++) {
			List<String> exception_types = exctstrings.get(i - 1);

			buf.append(" catch (");
			if(exception_types.size() > 1) { // multi-catch, Java 7 style
				for(int exc_index = 1; exc_index < exception_types.size(); ++exc_index) {
					VarType exc_type = new VarType(CodeConstants.TYPE_OBJECT, 0, exception_types.get(exc_index));
					String exc_type_name = ExprProcessor.getCastTypeName(exc_type);
					
					buf.append(exc_type_name + " | ");
				}
			}
			buf.append(vars.get(i-1).toJava(indent));
			buf.append(") {"+new_line_separator+ExprProcessor.jmpWrapper(stats.get(i), indent+1, true)+indstr+"}");
		}
		buf.append(new_line_separator);
		
		return buf.toString();
	}
	
	public Statement getSimpleCopy() {
		
		CatchStatement cs = new CatchStatement();
		
		for(List<String> exc : this.exctstrings) {
			cs.exctstrings.add(new ArrayList<String>(exc));
			cs.vars.add(new VarExprent(DecompilerContext.getCountercontainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER), 
					new VarType(CodeConstants.TYPE_OBJECT, 0, exc.get(0)), 
					(VarProcessor)DecompilerContext.getProperty(DecompilerContext.CURRENT_VAR_PROCESSOR)));
		}
		
		return cs;
	}
	
	// *****************************************************************************
	// getter and setter methods
	// *****************************************************************************
	
	public List<VarExprent> getVars() {
		return vars;
	}	
	
}
