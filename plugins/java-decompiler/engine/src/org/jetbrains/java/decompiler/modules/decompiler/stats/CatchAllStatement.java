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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.DecHelper;
import org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

public class CatchAllStatement extends Statement {
	
	private Statement handler;
	
	private boolean isFinally;
	
	private VarExprent monitor;
	
	private List<VarExprent> vars = new ArrayList<VarExprent>();  

	// *****************************************************************************
	// constructors
	// *****************************************************************************
	
	private CatchAllStatement(){
		type = Statement.TYPE_CATCHALL;  
	};
	
	private CatchAllStatement(Statement head, Statement handler) {
		
		this();
		
		first = head;
		stats.addWithKey(head, head.id);

		this.handler = handler;
		stats.addWithKey(handler, handler.id);
		
		List<StatEdge> lstSuccs = head.getSuccessorEdges(STATEDGE_DIRECT_ALL); 
		if(!lstSuccs.isEmpty()) {
			StatEdge edge = lstSuccs.get(0);
			if(edge.getType() == StatEdge.TYPE_REGULAR) {
				post = edge.getDestination(); 
			}
		}
		
		vars.add(new VarExprent(DecompilerContext.getCountercontainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER), 
				new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/Throwable"), 
				(VarProcessor)DecompilerContext.getProperty(DecompilerContext.CURRENT_VAR_PROCESSOR)));
		
	}
	

	// *****************************************************************************
	// public methods
	// *****************************************************************************
	
	public static Statement isHead(Statement head) {
		
		if(head.getLastBasicType() != Statement.LASTBASICTYPE_GENERAL) {
			return null;
		}

		HashSet<Statement> setHandlers = DecHelper.getUniquePredExceptions(head);

		if(setHandlers.size() != 1) {
			return null;
		}

		for(StatEdge edge : head.getSuccessorEdges(StatEdge.TYPE_EXCEPTION)) {
			Statement exc = edge.getDestination();
			
			if(edge.getExceptions() == null && setHandlers.contains(exc) && exc.getLastBasicType() == LASTBASICTYPE_GENERAL) {
				List<StatEdge> lstSuccs = exc.getSuccessorEdges(STATEDGE_DIRECT_ALL); 
				if(lstSuccs.isEmpty() || lstSuccs.get(0).getType() != StatEdge.TYPE_REGULAR) {
					
					if(head.isMonitorEnter() || exc.isMonitorEnter()) {
						return null;
					}
					
					if(DecHelper.checkStatementExceptions(Arrays.asList(new Statement[] {head, exc}))) {
						return new CatchAllStatement(head, exc);
					}
				}
			}
		}
		
		return null;
	}

	public String toJava(int indent) {
		String indstr = InterpreterUtil.getIndentString(indent);
		String indstr1 = null;		

		String new_line_separator = DecompilerContext.getNewLineSeparator();
				
		StringBuffer buf = new StringBuffer();
		
		buf.append(ExprProcessor.listToJava(varDefinitions, indent));

		boolean labeled = isLabeled(); 
		if(labeled) {
			buf.append(indstr+"label"+this.id+":" + new_line_separator);
		}
		
		List<StatEdge> lstSuccs = first.getSuccessorEdges(STATEDGE_DIRECT_ALL); 
		if(first.type == TYPE_TRYCATCH && first.varDefinitions.isEmpty() && isFinally &&
				!labeled && !first.isLabeled() && (lstSuccs.isEmpty() || !lstSuccs.get(0).explicit)) {
			String content = ExprProcessor.jmpWrapper(first, indent, true);
			content = content.substring(0, content.length()-new_line_separator.length());
			
			buf.append(content);
		} else {
			buf.append(indstr+"try {" + new_line_separator);
			buf.append(ExprProcessor.jmpWrapper(first, indent+1, true));
			buf.append(indstr+"}");
		}
		
		buf.append((isFinally?" finally":
			" catch ("+vars.get(0).toJava(indent)+")")+" {" + new_line_separator);
		
		if(monitor != null) {
			indstr1 = InterpreterUtil.getIndentString(indent+1);
			buf.append(indstr1+"if("+monitor.toJava(indent)+") {" + new_line_separator);
		}
		
		buf.append(ExprProcessor.jmpWrapper(handler, indent+1+(monitor != null?1:0), true));

		if(monitor != null) {
			buf.append(indstr1+"}" + new_line_separator);
		}

		buf.append(indstr+"}" + new_line_separator);
		
		return buf.toString();
	}
	
	public void replaceStatement(Statement oldstat, Statement newstat) {

		if(handler == oldstat) {
			handler = newstat;
		}

		super.replaceStatement(oldstat, newstat);
	}
	
	public Statement getSimpleCopy() {
		
		CatchAllStatement cas = new CatchAllStatement();
		
		cas.isFinally = this.isFinally;
		
		if(this.monitor != null) {
			cas.monitor = new VarExprent(DecompilerContext.getCountercontainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER), 
					VarType.VARTYPE_INT, 
					(VarProcessor)DecompilerContext.getProperty(DecompilerContext.CURRENT_VAR_PROCESSOR));
		}
		
		if(!this.vars.isEmpty()) {
			// FIXME: WTF??? vars?! 
			vars.add(new VarExprent(DecompilerContext.getCountercontainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER), 
					new VarType(CodeConstants.TYPE_OBJECT, 0, "java/lang/Throwable"), 
					(VarProcessor)DecompilerContext.getProperty(DecompilerContext.CURRENT_VAR_PROCESSOR)));
		}
		
		return cas;
	}
	
	public void initSimpleCopy() {
		first = stats.get(0);
		handler = stats.get(1);
	}
	
	// *****************************************************************************
	// getter and setter methods
	// *****************************************************************************

	public Statement getHandler() {
		return handler;
	}


	public void setHandler(Statement handler) {
		this.handler = handler;
	}


	public boolean isFinally() {
		return isFinally;
	}


	public void setFinally(boolean isFinally) {
		this.isFinally = isFinally;
	}


	public VarExprent getMonitor() {
		return monitor;
	}


	public void setMonitor(VarExprent monitor) {
		this.monitor = monitor;
	}

	public List<VarExprent> getVars() {
		return vars;
	}	
	
}
