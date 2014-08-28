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
import java.util.List;

import de.fernflower.modules.decompiler.exps.Exprent;
import de.fernflower.struct.gen.VarType;

public class CheckTypesResult {

	private List<ExprentTypePair> lstMaxTypeExprents = new ArrayList<ExprentTypePair>();

	private List<ExprentTypePair> lstMinTypeExprents = new ArrayList<ExprentTypePair>();
	
	public void addMaxTypeExprent(Exprent exprent, VarType type) {
		lstMaxTypeExprents.add(new ExprentTypePair(exprent, type, null));
	}

	public void addMinTypeExprent(Exprent exprent, VarType type) {
		lstMinTypeExprents.add(new ExprentTypePair(exprent, type, null));
	}
	
	public List<ExprentTypePair> getLstMaxTypeExprents() {
		return lstMaxTypeExprents;
	}

	public List<ExprentTypePair> getLstMinTypeExprents() {
		return lstMinTypeExprents;
	}
	
	public class ExprentTypePair {
		public Exprent exprent;
		public VarType type;
		public VarType desttype;
		
		public ExprentTypePair(Exprent exprent, VarType type, VarType desttype) {
			this.exprent = exprent;
			this.type = type;
			this.desttype = desttype;
		}
	}
	
}
