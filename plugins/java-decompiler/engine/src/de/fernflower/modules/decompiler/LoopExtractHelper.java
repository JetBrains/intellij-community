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

package de.fernflower.modules.decompiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import de.fernflower.modules.decompiler.stats.DoStatement;
import de.fernflower.modules.decompiler.stats.IfStatement;
import de.fernflower.modules.decompiler.stats.SequenceStatement;
import de.fernflower.modules.decompiler.stats.Statement;



public class LoopExtractHelper {

	
	
	public static boolean extractLoops(Statement root) {
		
		boolean res = (extractLoopsRec(root) != 0);
		
		if(res) {
			SequenceHelper.condenseSequences(root);
		}

		return res;
	}
	
	
	private static int extractLoopsRec(Statement stat) {

		boolean res = false;
		
		for(;;) {
			
			boolean updated = false;
			
			for(Statement st: stat.getStats()) {
				int extr = extractLoopsRec(st);
				res |= (extr != 0);
				
				if(extr == 2) {
					updated = true;
					break;
				}
			}
			
			if(!updated) {
				break;
			}
		}
		
		if(stat.type == Statement.TYPE_DO) {
			if(extractLoop((DoStatement)stat)) {
				return 2;
			}
		}
		
		return res?1:0;
	}
	
	
	
	private static boolean extractLoop(DoStatement stat) {

		if(stat.getLooptype() != DoStatement.LOOP_DO) {
			return false;
		}
		
		for(StatEdge edge: stat.getLabelEdges()) {
			if(edge.getType() != StatEdge.TYPE_CONTINUE && edge.getDestination().type != Statement.TYPE_DUMMYEXIT) {
				return false;
			}
		}
		
		if(!extractLastIf(stat)) {
			return extractFirstIf(stat);
		} else {
			return true;
		}
	}
	
	private static boolean extractLastIf(DoStatement stat) {
		
		// search for an if condition at the end of the loop
		Statement last = stat.getFirst();
		while(last.type == Statement.TYPE_SEQUENCE) {
			last = last.getStats().getLast();
		}
		
		if(last.type == Statement.TYPE_IF) {
			IfStatement lastif = (IfStatement)last;
			if(lastif.iftype == IfStatement.IFTYPE_IF && lastif.getIfstat() != null) {
				Statement ifstat = lastif.getIfstat();
				StatEdge elseedge = lastif.getAllSuccessorEdges().get(0);
				
				if(elseedge.getType() == StatEdge.TYPE_CONTINUE && elseedge.closure == stat) {
					
					Set<Statement> set = stat.getNeighboursSet(StatEdge.TYPE_CONTINUE, Statement.DIRECTION_BACKWARD);
					set.remove(last);
					
					if(set.isEmpty()) { // no direct continues in a do{}while loop
						if(isExternStatement(stat, ifstat, ifstat)) {
							extractIfBlock(stat, lastif);
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private static boolean extractFirstIf(DoStatement stat) {

		// search for an if condition at the entrance of the loop
		Statement first = stat.getFirst();
		while(first.type == Statement.TYPE_SEQUENCE) {
			first = first.getFirst();
		}
		
		// found an if statement
		if(first.type == Statement.TYPE_IF) {
			IfStatement firstif = (IfStatement)first;
			
			if(firstif.getFirst().getExprents().isEmpty()) {
				
				if(firstif.iftype == IfStatement.IFTYPE_IF && firstif.getIfstat()!=null) {
					Statement ifstat = firstif.getIfstat();
					
					if(isExternStatement(stat, ifstat, ifstat)) {
						extractIfBlock(stat, firstif);
						return true;
					}
				}
			}
		}
		return false;
	}
	
	
	
	private static boolean isExternStatement(DoStatement loop, Statement block, Statement stat) {
		
		for(StatEdge edge: stat.getAllSuccessorEdges()) {
			if(loop.containsStatement(edge.getDestination()) &&
					!block.containsStatement(edge.getDestination())) {
				return false;
			}
		}
		
		for(Statement st: stat.getStats()) {
			if(!isExternStatement(loop, block, st)) {
				return false;
			}
		}
		
		return true;
	}
	
	
	private static void extractIfBlock(DoStatement loop, IfStatement ifstat) {
		
		Statement target = ifstat.getIfstat();
		StatEdge ifedge = ifstat.getIfEdge();
		
		ifstat.setIfstat(null);
		ifedge.getSource().changeEdgeType(Statement.DIRECTION_FORWARD, ifedge, StatEdge.TYPE_BREAK);
		ifedge.closure = loop;
		ifstat.getStats().removeWithKey(target.id);

		loop.addLabeledEdge(ifedge);
		
		SequenceStatement block = new SequenceStatement(Arrays.asList(new Statement[] {loop, target}));
		loop.getParent().replaceStatement(loop, block);
		block.setAllParent();
		
		loop.addSuccessor(new StatEdge(StatEdge.TYPE_REGULAR, loop, target));
		
		for(StatEdge edge: new ArrayList<StatEdge>(block.getLabelEdges())) {
			if(edge.getType() == StatEdge.TYPE_CONTINUE || edge == ifedge) {
				loop.addLabeledEdge(edge);
			} 
		}
		
		for(StatEdge edge: block.getPredecessorEdges(StatEdge.TYPE_CONTINUE)) {
			if(loop.containsStatementStrict(edge.getSource())) {
				block.removePredecessor(edge);
				edge.getSource().changeEdgeNode(Statement.DIRECTION_FORWARD, edge, loop);
				loop.addPredecessor(edge);
			}
		}
	}
	
}
