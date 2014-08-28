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

package org.jetbrains.java.decompiler.modules.decompiler.exps;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPaar;
import org.jetbrains.java.decompiler.struct.gen.VarType;



public class Exprent {
	
	public static final int MULTIPLE_USES = 1;
	public static final int SIDE_EFFECTS_FREE = 2;
	public static final int BOTH_FLAGS = 3;
	
	
	public static final int EXPRENT_ARRAY = 1;
	public static final int EXPRENT_ASSIGNMENT = 2;
	public static final int EXPRENT_CONST = 3;
	public static final int EXPRENT_EXIT = 4;
	public static final int EXPRENT_FIELD = 5;
	public static final int EXPRENT_FUNCTION = 6;
	public static final int EXPRENT_IF = 7;
	public static final int EXPRENT_INVOCATION = 8;
	public static final int EXPRENT_MONITOR = 9;
	public static final int EXPRENT_NEW = 10;
	public static final int EXPRENT_SWITCH = 11;
	public static final int EXPRENT_VAR = 12;
	public static final int EXPRENT_ANNOTATION = 13;
	public static final int EXPRENT_ASSERT = 14;

	public int type;
	
	public int id;

	{
		// set exprent id
		id = DecompilerContext.getCountercontainer().getCounterAndIncrement(CounterContainer.EXPRENT_COUNTER);
	}
	
	public int getPrecedence() {
		return 0; // the highest precedence
	}
	
	public VarType getExprType() {
		return VarType.VARTYPE_VOID;
	}
	
	public int getExprentUse() {
		return 0;
	}

	public CheckTypesResult checkExprTypeBounds() {
		return new CheckTypesResult();
	}
	
	public boolean containsExprent(Exprent exprent) {

		List<Exprent> listTemp = new ArrayList<Exprent>(getAllExprents(true));
		listTemp.add(this);

		for(Exprent lstexpr : listTemp) {
			if(lstexpr.equals(exprent)) {
				return true;
			}
		}

		return false;
	}
	
	public List<Exprent> getAllExprents(boolean recursive) {
		List<Exprent> lst = getAllExprents();

		if(recursive) {
			for(int i=lst.size()-1;i>=0;i--) {
				lst.addAll(lst.get(i).getAllExprents(true));
			}
		}
		
		return lst;
	}

	public Set<VarVersionPaar> getAllVariables() {
		
		HashSet<VarVersionPaar> set = new HashSet<VarVersionPaar>();
		
		List<Exprent> lstAllExprents = getAllExprents(true);
		lstAllExprents.add(this);

		for(Exprent expr : lstAllExprents) {
			if(expr.type == Exprent.EXPRENT_VAR) {
				set.add(new VarVersionPaar((VarExprent)expr));
			}
		}
		
		return set;
	}
	
	public List<Exprent> getAllExprents() {
		throw new RuntimeException("not implemented");
	}
	
	public Exprent copy() {
		throw new RuntimeException("not implemented");
	}

	public String toJava(int indent) {
		throw new RuntimeException("not implemented");
	}
	
	public void replaceExprent(Exprent oldexpr, Exprent newexpr) {}
	
	
}
