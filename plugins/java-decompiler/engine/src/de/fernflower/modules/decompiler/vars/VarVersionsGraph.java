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

package org.jetbrains.java.decompiler.modules.decompiler.vars;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.jetbrains.java.decompiler.modules.decompiler.decompose.GenericDominatorEngine;
import org.jetbrains.java.decompiler.modules.decompiler.decompose.IGraph;
import org.jetbrains.java.decompiler.modules.decompiler.decompose.IGraphNode;
import org.jetbrains.java.decompiler.util.VBStyleCollection;


public class VarVersionsGraph {

	public int counter = 0;
	
	public VBStyleCollection<VarVersionNode, VarVersionPaar> nodes = new VBStyleCollection<VarVersionNode, VarVersionPaar>(); 

	private GenericDominatorEngine engine;
	
	public VarVersionNode createNode(VarVersionPaar ver) {
		VarVersionNode node;
		nodes.addWithKey(node = new VarVersionNode(ver.var, ver.version), ver);
		return node;
	}
	
	public void addNodes(Collection<VarVersionNode> colnodes, Collection<VarVersionPaar> colpaars) {
		nodes.addAllWithKey(colnodes, colpaars);
	}
	
	public boolean isDominatorSet(VarVersionNode node, HashSet<VarVersionNode> domnodes) {

		if(domnodes.size() == 1) {
			return engine.isDominator(node, domnodes.iterator().next());
		} else {
		
			HashSet<VarVersionNode> marked = new HashSet<VarVersionNode>();
	
			if(domnodes.contains(node)) {
				return true;
			}
	
			LinkedList<VarVersionNode> lstNodes = new LinkedList<VarVersionNode>();
			lstNodes.add(node);
	
			while(!lstNodes.isEmpty()) {
	
				VarVersionNode nd = lstNodes.remove(0);
				if(marked.contains(nd)) {
					continue;
				} else {
					marked.add(nd);
				}
	
				if(nd.preds.isEmpty()) {
					return false;
				}
	
				for(VarVersionEdge edge: nd.preds) {
					VarVersionNode pred = edge.source;
					if(!marked.contains(pred) && !domnodes.contains(pred)) {
						lstNodes.add(pred);
					}
				}
			}
		}
		
		return true;
	}
	
	public void initDominators() {

		final HashSet<VarVersionNode> roots = new HashSet<VarVersionNode>();

		for(VarVersionNode node: nodes) {
			if(node.preds.isEmpty()) {
				roots.add(node);
			}
		}

		engine = new GenericDominatorEngine(new IGraph() {
			public List<? extends IGraphNode> getReversePostOrderList() {
				return getReversedPostOrder(roots);
			}

			public Set<? extends IGraphNode> getRoots() {
				return new HashSet<IGraphNode>(roots);
			}
		});

		engine.initialize();
	}
	
	private LinkedList<VarVersionNode> getReversedPostOrder(Collection<VarVersionNode> roots) {
		
		LinkedList<VarVersionNode> lst = new LinkedList<VarVersionNode>();
		HashSet<VarVersionNode> setVisited = new HashSet<VarVersionNode>(); 
		
		for(VarVersionNode root: roots) {

			LinkedList<VarVersionNode> lstTemp = new LinkedList<VarVersionNode>();
			addToReversePostOrderListIterative(root, lstTemp, setVisited);
			
			lst.addAll(lstTemp);
		}
		
		return lst;
	}
	
	private void addToReversePostOrderListIterative(VarVersionNode root, List<VarVersionNode> lst, HashSet<VarVersionNode> setVisited) {
		
		HashMap<VarVersionNode, List<VarVersionEdge>> mapNodeSuccs = new HashMap<VarVersionNode, List<VarVersionEdge>>();
		
		LinkedList<VarVersionNode> stackNode = new LinkedList<VarVersionNode>();
		LinkedList<Integer> stackIndex = new LinkedList<Integer>();
		
		stackNode.add(root);
		stackIndex.add(0);
		
		while(!stackNode.isEmpty()) {
			
			VarVersionNode node = stackNode.getLast();
			int index = stackIndex.removeLast();

			setVisited.add(node);
			
			List<VarVersionEdge> lstSuccs = mapNodeSuccs.get(node);
			if(lstSuccs == null) {
				mapNodeSuccs.put(node, lstSuccs = new ArrayList<VarVersionEdge>(node.succs));
			}
			
			for(;index<lstSuccs.size();index++) {
				VarVersionNode succ = lstSuccs.get(index).dest;
				
				if(!setVisited.contains(succ)) {
					stackIndex.add(index+1);
					
					stackNode.add(succ);
					stackIndex.add(0);
					
					break;
				}
			}
			
			if(index == lstSuccs.size()) {
				lst.add(0, node);
				
				stackNode.removeLast();
			}
		}
		
	}
	
}
