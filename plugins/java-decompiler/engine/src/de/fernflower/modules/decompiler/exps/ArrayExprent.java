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

package de.fernflower.modules.decompiler.exps;

import java.util.ArrayList;
import java.util.List;

import de.fernflower.modules.decompiler.ExprProcessor;
import de.fernflower.modules.decompiler.vars.CheckTypesResult;
import de.fernflower.struct.gen.VarType;
import de.fernflower.util.InterpreterUtil;


public class ArrayExprent extends Exprent {

	private Exprent array;
	
	private Exprent index;
	
	private VarType hardtype;
	
	{
		this.type = EXPRENT_ARRAY;
	}
	
	public ArrayExprent(Exprent array, Exprent index, VarType hardtype) {
		this.array = array;
		this.index = index;
		this.hardtype = hardtype;
	}
	
	public Exprent copy() {
		return new ArrayExprent(array.copy(), index.copy(), hardtype);
	}

	public VarType getExprType() {
		VarType exprType = array.getExprType().copy();
		if(exprType.equals(VarType.VARTYPE_NULL)) {
			exprType = hardtype.copy();
		} else {
			exprType.decArrayDim();
		}
		
		return exprType;
	}
	
	public int getExprentUse() {
		return array.getExprentUse() & index.getExprentUse() & Exprent.MULTIPLE_USES;
	}

	public CheckTypesResult checkExprTypeBounds() {
		CheckTypesResult result = new CheckTypesResult();
		
		result.addMinTypeExprent(index, VarType.VARTYPE_BYTECHAR);
		result.addMaxTypeExprent(index, VarType.VARTYPE_INT);
		
		return result;
	}
	
	public List<Exprent> getAllExprents() {
		List<Exprent> lst = new ArrayList<Exprent>();
		lst.add(array);
		lst.add(index);
		return lst;
	}
	
	
	public String toJava(int indent) {
		String res = array.toJava(indent);
		
		if(array.getPrecedence() > getPrecedence()) { // array precedence equals 0
			res = "("+res+")";
		}
		
		VarType arrtype = array.getExprType();
		if(arrtype.arraydim == 0) {
			VarType objarr = VarType.VARTYPE_OBJECT.copy();
			objarr.arraydim = 1; // type family does not change
			
			res = "(("+ExprProcessor.getCastTypeName(objarr)+")"+res+")";
		}
		
		return res+"["+index.toJava(indent)+"]";
	}

	public boolean equals(Object o) {
    if(o == this) return true;
    if(o == null || !(o instanceof ArrayExprent)) return false;

    ArrayExprent arr = (ArrayExprent)o;
    return InterpreterUtil.equalObjects(array, arr.getArray()) &&
        InterpreterUtil.equalObjects(index, arr.getIndex());
  }

	public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
		if(oldexpr == array) {
			array = newexpr;
		}
		
		if(oldexpr == index) {
			index = newexpr;
		}
	}
	
	public Exprent getArray() {
		return array;
	}

	public Exprent getIndex() {
		return index;
	}
	
}
