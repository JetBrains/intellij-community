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

package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.util.ListStack;

public class ExprentStack extends ListStack<Exprent> {
	
	public ExprentStack() {} 
	
	public ExprentStack(ListStack<Exprent> list) {
		super(list);
		pointer = list.getPointer();
	}
	
	public Exprent push(Exprent item) {
		super.push(item);
		
		return item;
	}
	
	public Exprent pop() {

		Exprent o = this.remove(--pointer);
		
		return o;
	}
	
	public ExprentStack clone() {
		return new ExprentStack(this);
	}
	
}
