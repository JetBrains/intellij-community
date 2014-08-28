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

import java.util.LinkedList;

import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;


public class ClearStructHelper {

	public static void clearStatements(RootStatement root) {
		
		LinkedList<Statement> stack = new LinkedList<Statement>();
		stack.add(root);
		
		while(!stack.isEmpty()) {
			
			Statement stat = stack.removeFirst();

			stat.clearTempInformation();
			
			stack.addAll(stat.getStats());
		}
		
	}
	
}
