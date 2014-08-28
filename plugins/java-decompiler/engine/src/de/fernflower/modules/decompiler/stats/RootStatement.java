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

package de.fernflower.modules.decompiler.stats;

import de.fernflower.modules.decompiler.ExprProcessor;


public class RootStatement extends Statement {

	private Statement dummyExit; 
	
	public RootStatement(Statement head, Statement dummyExit) {

		type = Statement.TYPE_ROOT;
		
		first = head;
		this.dummyExit = dummyExit;
		
		stats.addWithKey(first, first.id);
		first.setParent(this);
		
	}
	
	public String toJava(int indent) {
		return ExprProcessor.listToJava(varDefinitions, indent)+
				first.toJava(indent);
	}

	public Statement getDummyExit() {
		return dummyExit;
	}

	public void setDummyExit(Statement dummyExit) {
		this.dummyExit = dummyExit;
	}
	
}
