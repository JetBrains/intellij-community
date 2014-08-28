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

import de.fernflower.modules.decompiler.vars.CheckTypesResult;
import de.fernflower.struct.gen.VarType;
import de.fernflower.util.InterpreterUtil;


public class SwitchExprent extends Exprent {

	private Exprent value;
	
	private List<List<ConstExprent>> caseValues = new ArrayList<List<ConstExprent>>();
	
	{
		this.type = EXPRENT_SWITCH;
	}
	
	public SwitchExprent(Exprent value) {
		this.value = value;
	}
	
	public Exprent copy() {
		SwitchExprent swexpr = new SwitchExprent(value.copy());
		
		List<List<ConstExprent>> lstCaseValues = new ArrayList<List<ConstExprent>>();
		for(List<ConstExprent> lst: caseValues) {
			lstCaseValues.add(new ArrayList<ConstExprent>(lst));
		}
		swexpr.setCaseValues(lstCaseValues);
		
		return swexpr;
	}
	
	public VarType getExprType() {
		return value.getExprType();
	}
	
	public CheckTypesResult checkExprTypeBounds() {
		CheckTypesResult result = new CheckTypesResult();

		result.addMinTypeExprent(value, VarType.VARTYPE_BYTECHAR);
		result.addMaxTypeExprent(value, VarType.VARTYPE_INT);
		
		VarType valtype = value.getExprType();
		for(List<ConstExprent> lst: caseValues) {
			for(ConstExprent expr: lst) {
				if(expr != null) {
					VarType casetype = expr.getExprType();
					if(!casetype.equals(valtype)) {
						valtype = VarType.getCommonSupertype(casetype, valtype);
						result.addMinTypeExprent(value, valtype);
					}
				}
			}
		}
		
		return result;
	}
	
	public List<Exprent> getAllExprents() {
		List<Exprent> lst = new ArrayList<Exprent>();
		lst.add(value);
		return lst;
	}
	
	public String toJava(int indent) {
		return "switch("+value.toJava(indent)+")";
	}

	public boolean equals(Object o) {
		if(o == this) {
			return true;
		}
		
		if(o == null || !(o instanceof SwitchExprent)) {
			return false;
		}

		SwitchExprent sw = (SwitchExprent) o;
		return InterpreterUtil.equalObjects(value, sw.getValue());
	}
	
	public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
		if(oldexpr == value) {
			value = newexpr;
		} 
	}
	
	public Exprent getValue() {
		return value;
	}

	public void setCaseValues(List<List<ConstExprent>> caseValues) {
		this.caseValues = caseValues;
	}
}
