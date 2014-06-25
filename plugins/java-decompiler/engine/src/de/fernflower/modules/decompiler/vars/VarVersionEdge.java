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

public class VarVersionEdge { // FIXME: can be removed? 

	public static final int EDGE_GENERAL = 0; 
	public static final int EDGE_PHANTOM = 1;
	
	public int type; 
	
	public VarVersionNode source;
	
	public VarVersionNode dest;
	
	private int hashCode;
	
	public VarVersionEdge(int type, VarVersionNode source, VarVersionNode dest) {
		this.type = type;
		this.source = source;
		this.dest = dest;
		this.hashCode = source.hashCode() ^ dest.hashCode() + type;
	}

	@Override
	public boolean equals(Object o) {
    if(o == this) return true;
		if(o == null || !(o instanceof VarVersionEdge)) return false;

		VarVersionEdge edge = (VarVersionEdge)o;
		return type == edge.type && source == edge.source && dest == edge.dest;
	}

	@Override
	public int hashCode() {
		return hashCode;
	}
	
	@Override
	public String toString() {
		return source.toString() + " ->" + type + "-> " + dest.toString();
	}
	
}
