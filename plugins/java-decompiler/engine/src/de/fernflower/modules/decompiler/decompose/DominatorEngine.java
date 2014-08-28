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

package de.fernflower.modules.decompiler.decompose;

import java.util.List;

import de.fernflower.modules.decompiler.StatEdge;
import de.fernflower.modules.decompiler.stats.Statement;
import de.fernflower.util.VBStyleCollection;

public class DominatorEngine {

	private Statement statement; 

	private VBStyleCollection<Integer, Integer> colOrderedIDoms = new VBStyleCollection<Integer, Integer>(); 
	
	
	public DominatorEngine(Statement statement) {
		this.statement = statement;
	}
	
	public void initialize() {
		calcIDoms();
	}
	
	private void orderStatements() {

		for(Statement stat : statement.getReversePostOrderList()) {
			colOrderedIDoms.addWithKey(null, stat.id);
		}
		
	}
	
	private Integer getCommonIDom(Integer key1, Integer key2, VBStyleCollection<Integer, Integer> orderedIDoms) {
		
		if(key1 == null) {
			return key2;
		} else if(key2 == null) {
			return key1;
		}
		
		int index1 = orderedIDoms.getIndexByKey(key1); 
		int index2 = orderedIDoms.getIndexByKey(key2);
		
		while(index1 != index2) {
			if(index1 > index2) {
				key1 = orderedIDoms.getWithKey(key1);
				index1 = orderedIDoms.getIndexByKey(key1);
			} else {
				key2 = orderedIDoms.getWithKey(key2);
				index2 = orderedIDoms.getIndexByKey(key2);
			}
		}
		
		return key1;
	}
	
	private void calcIDoms() {
		
		orderStatements();
		
		colOrderedIDoms.putWithKey(statement.getFirst().id, statement.getFirst().id);
		
		// exclude first statement
		List<Integer> lstIds = colOrderedIDoms.getLstKeys().subList(1, colOrderedIDoms.getLstKeys().size()); 
		
		for(;;) {
			
			boolean changed = false;
				
			for(Integer id : lstIds) {
				
				Statement stat = statement.getStats().getWithKey(id);
				Integer idom = null;
				
				for(StatEdge edge : stat.getAllPredecessorEdges()) {
					if(colOrderedIDoms.getWithKey(edge.getSource().id) != null) {
						idom = getCommonIDom(idom, edge.getSource().id, colOrderedIDoms);
					}
				}
				
				Integer oldidom = colOrderedIDoms.putWithKey(idom, id);
				if(!idom.equals(oldidom)) {
					changed = true;
				}
			}
			
			if(!changed) {
				break;
			}
		}
		
	}

	public VBStyleCollection<Integer, Integer> getOrderedIDoms() {
		return colOrderedIDoms;
	}

	public boolean isDominator(Integer node, Integer dom) {
	
		while(!node.equals(dom)) {

			Integer idom = colOrderedIDoms.getWithKey(node);
			
			if(idom.equals(node)) {
				return false; // root node
			} else {
				node = idom;
			}
		}
		
		return true;
	}
	
}
