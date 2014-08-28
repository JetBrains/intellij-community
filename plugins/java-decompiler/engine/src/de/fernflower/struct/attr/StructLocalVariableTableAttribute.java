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

package de.fernflower.struct.attr;

import java.util.HashMap;

import de.fernflower.struct.consts.ConstantPool;

public class StructLocalVariableTableAttribute extends StructGeneralAttribute {

	private HashMap<Integer, String> mapVarNames = new HashMap<Integer, String>();
	
	public void initContent(ConstantPool pool) {

		name = ATTRIBUTE_LOCAL_VARIABLE_TABLE;

		int len = ((info[0] & 0xFF)<<8) | (info[1] & 0xFF);
		
		int ind = 6; 
		for(int i=0;i<len;i++, ind+=10) {
			int nindex = ((info[ind] & 0xFF)<<8) | (info[ind+1] & 0xFF);
			int vindex = ((info[ind+4] & 0xFF)<<8) | (info[ind+5] & 0xFF);
			
			mapVarNames.put(vindex, pool.getPrimitiveConstant(nindex).getString());
		}
	}

	public void addLocalVariableTable(StructLocalVariableTableAttribute attr) {
		mapVarNames.putAll(attr.getMapVarNames());
	}
	
	public HashMap<Integer, String> getMapVarNames() {
		return mapVarNames;
	}
}
