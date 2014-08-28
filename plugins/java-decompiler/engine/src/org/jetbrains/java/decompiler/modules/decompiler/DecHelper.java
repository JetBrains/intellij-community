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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;


public class DecHelper {
	
	public static boolean checkStatementExceptions(List<Statement> lst) {
		
		Set<Statement> all = new HashSet<Statement>(lst);
		
		Set<Statement> handlers = new HashSet<Statement>();
		Set<Statement> intersection = null;
		
		for(Statement stat : lst) {
			Set<Statement> setNew = stat.getNeighboursSet(StatEdge.TYPE_EXCEPTION, Statement.DIRECTION_FORWARD);
			
			if(intersection == null) {
				intersection = setNew;
			} else {
				HashSet<Statement> interclone = new HashSet<Statement>(intersection);
				interclone.removeAll(setNew);
				
				intersection.retainAll(setNew);

				setNew.removeAll(intersection);
				
				handlers.addAll(interclone);
				handlers.addAll(setNew);
			}
		}
		
		for(Statement stat : handlers) {
			if(!all.contains(stat) || !all.containsAll(stat.getNeighbours(StatEdge.TYPE_EXCEPTION, Statement.DIRECTION_BACKWARD))) {
				return false;
			}
		}
		
		// check for other handlers (excluding head)
		for(int i=1;i<lst.size();i++) {
			Statement stat = lst.get(i);
			if(!stat.getPredecessorEdges(StatEdge.TYPE_EXCEPTION).isEmpty() && !handlers.contains(stat)) {
				return false;
			}
		}
		
		return true;
	}
	
	public static boolean isChoiceStatement(Statement head, List<Statement> lst) {
		
		Statement post = null;
		
		Set<Statement> setDest = head.getNeighboursSet(StatEdge.TYPE_REGULAR, Statement.DIRECTION_FORWARD);

		if(setDest.contains(head)) {
			return false;
		}
		
		for(;;) {
			
			lst.clear();
			
			boolean repeat = false;
		
			setDest.remove(post);
			Iterator<Statement> it = setDest.iterator();
			
			while(it.hasNext()) {
				Statement stat = it.next();
				
				if(stat.getLastBasicType() != Statement.LASTBASICTYPE_GENERAL) {
					if(post == null) {
						post = stat;
						repeat = true;
						break;
					} else {
						return false;
					}
				}
				
				// preds
				Set<Statement> setPred = stat.getNeighboursSet(StatEdge.TYPE_REGULAR, Statement.DIRECTION_BACKWARD);
				setPred.remove(head);
				if(setPred.contains(stat)) {
					return false;
				}
				
				if(!setDest.containsAll(setPred) || setPred.size()>1) {
					if(post == null) {
						post = stat;
						repeat = true;
						break;
					} else {
						return false;
					}
				} else if(setPred.size() == 1) {
					Statement pred = setPred.iterator().next();
					while(lst.contains(pred)) {
						Set<Statement> setPredTemp = pred.getNeighboursSet(StatEdge.TYPE_REGULAR, Statement.DIRECTION_BACKWARD);
						setPredTemp.remove(head);
						
						if(!setPredTemp.isEmpty()) { // at most 1 predecessor
							pred = setPredTemp.iterator().next();
							if(pred == stat) {
								return false;  // loop found
 							}
						} else {
							break;
						}
					}
				}
				
				// succs
				List<StatEdge> lstEdges = stat.getSuccessorEdges(Statement.STATEDGE_DIRECT_ALL);
				if(lstEdges.size() > 1) {
					Set<Statement> setSucc = stat.getNeighboursSet(Statement.STATEDGE_DIRECT_ALL, Statement.DIRECTION_FORWARD);
					setSucc.retainAll(setDest);
					
					if(setSucc.size()>0) {
						return false; 
					} else {
						if(post == null) {
							post = stat;
							repeat = true;
							break;
						} else {
							return false;
						}
					}
				} else if(lstEdges.size() == 1) {
					
					StatEdge edge = lstEdges.get(0);
					if(edge.getType() == StatEdge.TYPE_REGULAR) {
						Statement statd = edge.getDestination();
						if(head == statd) {
							return false;
						}
						if(!setDest.contains(statd) && post!=statd) {
							if(post!=null) {
								return false;
							} else {
								Set<Statement> set = statd.getNeighboursSet(StatEdge.TYPE_REGULAR, Statement.DIRECTION_BACKWARD);
								if(set.size()>1){
									post = statd;
									repeat = true;
									break;
								} else {
									return false;
								}
							}
						}
					}
				}
				
				lst.add(stat);
			}
			
			if(!repeat) {
				break;
			}
			
		}
		
		lst.add(head);
		lst.remove(post);
		
		lst.add(0, post);
		
		return true;
		
	}
	
	
	public static HashSet<Statement> getUniquePredExceptions(Statement head) {
		
		HashSet<Statement> setHandlers = new HashSet<Statement>(head.getNeighbours(StatEdge.TYPE_EXCEPTION, Statement.DIRECTION_FORWARD));
		
		Iterator<Statement> it = setHandlers.iterator();
		while(it.hasNext()) {
			if(it.next().getPredecessorEdges(StatEdge.TYPE_EXCEPTION).size()>1) {
				it.remove(); 
			}
		}
		return setHandlers;
	}
	
	public static List<Exprent> copyExprentList(List<Exprent> lst) {
		List<Exprent> ret = new ArrayList<Exprent>();
		for(Exprent expr: lst) {
			ret.add(expr.copy());
		}
		return ret;
	}
 	
}
