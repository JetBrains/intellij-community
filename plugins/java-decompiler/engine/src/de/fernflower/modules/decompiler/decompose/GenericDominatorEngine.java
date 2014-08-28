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
import java.util.Set;

import de.fernflower.util.VBStyleCollection;

public class GenericDominatorEngine {

	private IGraph graph; 

	private VBStyleCollection<IGraphNode, IGraphNode> colOrderedIDoms = new VBStyleCollection<IGraphNode, IGraphNode>(); 
	
	private Set<? extends IGraphNode> setRoots;
	
	public GenericDominatorEngine(IGraph graph) {
		this.graph = graph;
	}
	
	public void initialize() {
		calcIDoms();
	}
	
	private void orderNodes() {

		setRoots = graph.getRoots();

		for(IGraphNode node : graph.getReversePostOrderList()) {
			colOrderedIDoms.addWithKey(null, node);
		}
		
	}
	
	private IGraphNode getCommonIDom(IGraphNode node1, IGraphNode node2, VBStyleCollection<IGraphNode, IGraphNode> orderedIDoms) {
		
		IGraphNode nodeOld;
		
		if(node1 == null) {
			return node2;
		} else if(node2 == null) {
			return node1;
		}
		
		int index1 = orderedIDoms.getIndexByKey(node1); 
		int index2 = orderedIDoms.getIndexByKey(node2);
		
		while(index1 != index2) {
			if(index1 > index2) {
				nodeOld = node1;
				node1 = orderedIDoms.getWithKey(node1);
				
				if(nodeOld == node1) { // no idom - root or merging point
					return null;
				}
				
				index1 = orderedIDoms.getIndexByKey(node1);
			} else {
				nodeOld = node2;
				node2 = orderedIDoms.getWithKey(node2);

				if(nodeOld == node2) { // no idom - root or merging point
					return null;
				}
				
				index2 = orderedIDoms.getIndexByKey(node2);
			}
		}
		
		return node1;
	}
	
	private void calcIDoms() {

		orderNodes();

		List<IGraphNode> lstNodes = colOrderedIDoms.getLstKeys();  

		for(;;) {

			boolean changed = false;

			for(IGraphNode node : lstNodes) {

				IGraphNode idom = null;

				if(!setRoots.contains(node)) {
					for(IGraphNode pred : node.getPredecessors()) {
						if(colOrderedIDoms.getWithKey(pred) != null) {
							idom = getCommonIDom(idom, pred, colOrderedIDoms);
							if(idom == null) {
								break; // no idom found: merging point of two trees
							}
						}
					}
				}

				if(idom == null) {
					idom = node;
				}

				IGraphNode oldidom = colOrderedIDoms.putWithKey(idom, node);
				if(!idom.equals(oldidom)) { // oldidom is null iff the node is touched for the first time  
					changed = true;
				}
			}

			if(!changed) {
				break;
			}
		}

	}

	public VBStyleCollection<IGraphNode, IGraphNode> getOrderedIDoms() {
		return colOrderedIDoms;
	}

	public boolean isDominator(IGraphNode node, IGraphNode dom) {
	
		while(!node.equals(dom)) {

			IGraphNode idom = colOrderedIDoms.getWithKey(node);
			
			if(idom == node) {
				return false; // root node or merging point
			} else if(idom == null) {
				throw new RuntimeException("Inconsistent idom sequence discovered!"); 
			} else {
				node = idom;
			}
		}
		
		return true;
	}
	
}
