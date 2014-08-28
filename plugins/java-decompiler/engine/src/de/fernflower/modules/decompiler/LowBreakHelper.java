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

package org.jetbrains.java.decompiler.modules.decompiler;

import java.util.List;

import org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.SynchronizedStatement;

public class LowBreakHelper {
	
	public static void lowBreakLabels(Statement root) {

		lowBreakLabelsRec(root);
		
		liftBreakLabels(root);
	}
	
	private static void lowBreakLabelsRec(Statement stat) {

		for(;;) {
		
			boolean found = false;
			
			for(StatEdge edge: stat.getLabelEdges()) {
				if(edge.getType() == StatEdge.TYPE_BREAK) {
					Statement minclosure = getMinClosure(stat, edge.getSource());
					if(minclosure != stat) {
						minclosure.addLabeledEdge(edge);
						edge.labeled = isBreakEdgeLabeled(edge.getSource(), minclosure);
						found = true;
						break;
					}
				}
			}
			
			if(!found) {
				break;
			}
		}
		
		for(Statement st: stat.getStats()) {
			lowBreakLabelsRec(st);
		}
		
	}
	
	public static boolean isBreakEdgeLabeled(Statement source, Statement closure) {
		
		if(closure.type == Statement.TYPE_DO || closure.type == Statement.TYPE_SWITCH) {
			
			Statement parent = source.getParent();
			
			if(parent == closure) {
				 return false;
			} else {
				return isBreakEdgeLabeled(parent, closure) || 
						(parent.type == Statement.TYPE_DO || parent.type == Statement.TYPE_SWITCH);
			}
		} else {
			return true;
		}
	}
	
	public static Statement getMinClosure(Statement closure, Statement source) {
		
		for(;;) {
			
			Statement newclosure = null;
			
			switch(closure.type) {
			case Statement.TYPE_SEQUENCE:
				Statement last = closure.getStats().getLast();
				
				if(isOkClosure(closure, source, last)) {
					newclosure = last;
				}
				break;
			case Statement.TYPE_IF:
				IfStatement ifclosure = (IfStatement)closure;
				if(isOkClosure(closure, source, ifclosure.getIfstat())) {
					newclosure = ifclosure.getIfstat();
				} else if(isOkClosure(closure, source, ifclosure.getElsestat())) {
					newclosure = ifclosure.getElsestat();
				} 
				break;
			case Statement.TYPE_TRYCATCH:
				for(Statement st: closure.getStats()) {
					if(isOkClosure(closure, source, st)) {
						newclosure = st;
						break;
					}
				}
				break;
			case Statement.TYPE_SYNCRONIZED:
				Statement body = ((SynchronizedStatement)closure).getBody();
				
				if(isOkClosure(closure, source, body)) {
					newclosure = body;
				}
			}
			
			if(newclosure == null) {
				break;
			} 

			closure = newclosure;
		}
		
		return closure;
	}
	
	private static boolean isOkClosure(Statement closure, Statement source, Statement stat) {
		
		boolean ok = false;
		
		if(stat != null && stat.containsStatementStrict(source)) {
			
			List<StatEdge> lst = stat.getAllSuccessorEdges();
			
			ok = lst.isEmpty();
			if(!ok) {
				StatEdge edge = lst.get(0);
				ok = (edge.closure == closure && edge.getType() == StatEdge.TYPE_BREAK);
			}
		}
		
		return ok;
	}
	
	
	private static void liftBreakLabels(Statement stat) {
		
		for(Statement st: stat.getStats()) {
			liftBreakLabels(st);
		}

		
		for(;;) {
			
			boolean found = false;
			
			for(StatEdge edge: stat.getLabelEdges()) {
				if(edge.explicit && edge.labeled && edge.getType() == StatEdge.TYPE_BREAK) {
					
					Statement newclosure = getMaxBreakLift(stat, edge);
					
					if(newclosure != null) {
						newclosure.addLabeledEdge(edge);
						edge.labeled = isBreakEdgeLabeled(edge.getSource(), newclosure);
						
						found = true;
						break;
					}
				}
			}
			
			if(!found) {
				break;
			}
		}
		
	}

	private static Statement getMaxBreakLift(Statement stat, StatEdge edge) {
		
		Statement closure = null;
		Statement newclosure = stat;
		
		while((newclosure = getNextBreakLift(newclosure, edge)) != null) {
			closure = newclosure;
		}
		
		return closure;
	}
	
	private static Statement getNextBreakLift(Statement stat, StatEdge edge) {
		
		Statement closure = stat.getParent();

		while(closure!=null && !closure.containsStatementStrict(edge.getDestination())) {

			boolean labeled = isBreakEdgeLabeled(edge.getSource(), closure);
			if(closure.isLabeled() || !labeled) {
				return closure;
			}
			
			closure = closure.getParent();
		}
		
		return null;
	}
	
}
