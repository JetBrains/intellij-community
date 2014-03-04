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

package de.fernflower.modules.decompiler;

import java.util.ArrayList;
import java.util.List;

import de.fernflower.modules.decompiler.exps.Exprent;

public class PrimitiveExprsList {

	private List<Exprent> lstExprents = new ArrayList<Exprent>();  
	
	private ExprentStack stack = new ExprentStack();
	
	public PrimitiveExprsList() {}
	
	public PrimitiveExprsList copyStack() {
		PrimitiveExprsList prlst = new PrimitiveExprsList();
		prlst.setStack(stack.clone());
		return prlst;
	}
	
	public List<Exprent> getLstExprents() {
		return lstExprents;
	}

	public ExprentStack getStack() {
		return stack;
	}

	public void setStack(ExprentStack stack) {
		this.stack = stack;
	}
}
