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

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.java.decompiler.struct.consts.ConstantPool;

public class StructInnerClassesAttribute extends StructGeneralAttribute {

	private List<int[]> classentries = new ArrayList<int[]>(); 

	private List<String[]> stringentries = new ArrayList<String[]>(); 
	
	public void initContent(ConstantPool pool) {
		
		name = ATTRIBUTE_INNER_CLASSES;

		int length = 2+(((info[0] & 0xFF)<<8) | (info[1] & 0xFF))*8;
		int i=2;
		
		while(i<length) {
			
			int[] arr = new int[4];
			for(int j=0;j<4;j++) {
				arr[j] = ((info[i] & 0xFF)<<8) | (info[i+1] & 0xFF);
				i+=2;
			}

			classentries.add(arr);
		}
		
		for(int[] entry: classentries) {
			
			String[] arr = new String[3];
			// inner name
			arr[0] = pool.getPrimitiveConstant(entry[0]).getString();
			//enclosing class
			if(entry[1] != 0) {
				arr[1] = pool.getPrimitiveConstant(entry[1]).getString();
			} 
			// original simple name
			if(entry[2]!=0) {
				arr[2] = pool.getPrimitiveConstant(entry[2]).getString();
			}
			
			stringentries.add(arr);
		}
		
	}

	public List<int[]> getClassentries() {
		return classentries;
	}

	public List<String[]> getStringentries() {
		return stringentries;
	}

}
