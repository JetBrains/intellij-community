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

package org.jetbrains.java.decompiler.main.collectors;

public class CounterContainer {

	public static final int STATEMENT_COUNTER = 0;
	public static final int EXPRENT_COUNTER = 1;
	public static final int VAR_COUNTER = 2;
	
	private int[] values = new int[]{1, 1, 1};
	
	public void setCounter(int counter, int value) {
		values[counter] = value;
	}

	public int getCounter(int counter) {
		return values[counter];
	}

	public int getCounterAndIncrement(int counter) {
		return values[counter]++;
	}
	
}
