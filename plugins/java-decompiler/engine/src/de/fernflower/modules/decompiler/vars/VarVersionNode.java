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

package de.fernflower.modules.decompiler.vars;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.fernflower.modules.decompiler.decompose.IGraphNode;
import de.fernflower.util.SFormsFastMapDirect;

public class VarVersionNode implements IGraphNode {
	
	public static final int FLAG_PHANTOM_FINEXIT = 2;
	
	public int var;
	
	public int version;
	
	public Set<VarVersionEdge> succs = new HashSet<VarVersionEdge>();
	
	public Set<VarVersionEdge> preds = new HashSet<VarVersionEdge>();

	public int flags;
	
	public SFormsFastMapDirect live = new SFormsFastMapDirect(); 
	

	public VarVersionNode(int var, int version) {
		this.var = var;
		this.version = version;
	}

	public VarVersionPaar getVarPaar() {
		return new VarVersionPaar(var, version);
	}
	
	public List<IGraphNode> getPredecessors() {
		List<IGraphNode> lst = new ArrayList<IGraphNode>(preds.size());
		for(VarVersionEdge edge : preds) {
			lst.add(edge.source);
		}
		return lst;
	}

	public void removeSuccessor(VarVersionEdge edge) {
		succs.remove(edge);
	}
	
	public void removePredecessor(VarVersionEdge edge) {
		preds.remove(edge);
	}
	
	public void addSuccessor(VarVersionEdge edge) {
		succs.add(edge);
	}
	
	public void addPredecessor(VarVersionEdge edge) {
		preds.add(edge);
	}

	@Override
	public String toString() {
		return "("+var+"_"+version+")";
	}
	
}
