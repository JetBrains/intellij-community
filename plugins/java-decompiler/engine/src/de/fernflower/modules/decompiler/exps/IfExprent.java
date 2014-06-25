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
import java.util.Arrays;
import java.util.List;

import de.fernflower.struct.gen.VarType;
import de.fernflower.util.InterpreterUtil;
import de.fernflower.util.ListStack;


public class IfExprent extends Exprent {
	
	public static final int IF_EQ = 0;
	public static final int IF_NE = 1;
	public static final int IF_LT = 2;
	public static final int IF_GE = 3;
	public static final int IF_GT = 4;
	public static final int IF_LE = 5;

	public static final int IF_NULL = 6;
	public static final int IF_NONNULL = 7;
	
	public static final int IF_ICMPEQ = 8;
	public static final int IF_ICMPNE = 9;
	public static final int IF_ICMPLT = 10;
	public static final int IF_ICMPGE = 11;
	public static final int IF_ICMPGT = 12;
	public static final int IF_ICMPLE = 13;
	public static final int IF_ACMPEQ = 14;
	public static final int IF_ACMPNE = 15;

	public static final int IF_CAND = 16;
	public static final int IF_COR = 17;

	public static final int IF_NOT = 18;
	public static final int IF_VALUE = 19;
	
	private static final int[] functypes = new int[] {
		FunctionExprent.FUNCTION_EQ,
		FunctionExprent.FUNCTION_NE,
		FunctionExprent.FUNCTION_LT,
		FunctionExprent.FUNCTION_GE,
		FunctionExprent.FUNCTION_GT,
		FunctionExprent.FUNCTION_LE,
		FunctionExprent.FUNCTION_EQ,
		FunctionExprent.FUNCTION_NE,
		FunctionExprent.FUNCTION_EQ,
		FunctionExprent.FUNCTION_NE,
		FunctionExprent.FUNCTION_LT,
		FunctionExprent.FUNCTION_GE,
		FunctionExprent.FUNCTION_GT,
		FunctionExprent.FUNCTION_LE,
		FunctionExprent.FUNCTION_EQ,
		FunctionExprent.FUNCTION_NE,
		FunctionExprent.FUNCTION_CADD,
		FunctionExprent.FUNCTION_COR,
		FunctionExprent.FUNCTION_BOOLNOT,
		-1
	};
	
	private Exprent condition;
	
	{
		this.type = EXPRENT_IF;
	}
	
	public IfExprent(int iftype, ListStack<Exprent> stack) {
		
		if(iftype <= IF_LE) {
			stack.push(new ConstExprent(0, true));
		} else if(iftype <= IF_NONNULL) {
			stack.push(new ConstExprent(VarType.VARTYPE_NULL, null));
		}
		
		if(iftype == IF_VALUE) {
			condition = stack.pop();
		} else {
			condition = new FunctionExprent(functypes[iftype], stack);
		}
	}
	
	private IfExprent(Exprent condition) {
		this.condition = condition;
	}

	public Exprent copy() {
		return new IfExprent(condition.copy());
	}
	
	public List<Exprent> getAllExprents() {
		List<Exprent> lst = new ArrayList<Exprent>();
		lst.add(condition);
		return lst;
	}
	
	public String toJava(int indent) {
		StringBuffer buf = new StringBuffer("if(");
		buf.append(condition.toJava(indent));
		buf.append(")");

		return buf.toString();
	}
	
	public boolean equals(Object o) {
    if(o == this) return true;
    if(o == null || !(o instanceof IfExprent)) return false;

    IfExprent ie = (IfExprent)o;
    return InterpreterUtil.equalObjects(condition, ie.getCondition());
  }

	public void replaceExprent(Exprent oldexpr, Exprent newexpr) {
		if(oldexpr == condition) {
			condition = newexpr;
		} 
	}
	
	public IfExprent negateIf() {
		condition = new FunctionExprent(FunctionExprent.FUNCTION_BOOLNOT, 
				Arrays.asList(new Exprent[]{condition}));
		return this;
	}
			
	public Exprent getCondition() {
		return condition;
	}

	public void setCondition(Exprent condition) {
		this.condition = condition;
	}
}
