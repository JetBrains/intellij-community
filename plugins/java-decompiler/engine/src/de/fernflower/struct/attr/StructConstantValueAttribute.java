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

package org.jetbrains.java.decompiler.struct.attr;

import org.jetbrains.java.decompiler.struct.consts.ConstantPool;

public class StructConstantValueAttribute extends StructGeneralAttribute {

	private int index;

	public void initContent(ConstantPool pool) {

		name = ATTRIBUTE_CONSTANT_VALUE;
		index = ((info[0] & 0xFF)<<8) | (info[1] & 0xFF);
	}
	
	public int getIndex() {
		return index;
	}
	
	
}
