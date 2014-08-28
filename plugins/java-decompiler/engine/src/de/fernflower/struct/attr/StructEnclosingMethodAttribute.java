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
import org.jetbrains.java.decompiler.struct.consts.LinkConstant;

public class StructEnclosingMethodAttribute extends StructGeneralAttribute {

	private String classname;
	
	private String mtname;
	
	private String methodDescriptor;
	
	public void initContent(ConstantPool pool) {

		name = ATTRIBUTE_ENCLOSING_METHOD;

		int clindex = (((info[0] & 0xFF)<<8) | (info[1] & 0xFF));
		int mtindex = (((info[2] & 0xFF)<<8) | (info[3] & 0xFF));
		
		classname = pool.getPrimitiveConstant(clindex).getString();
		if(mtindex != 0) {
			LinkConstant lk = pool.getLinkConstant(mtindex);
			
			mtname = lk.elementname;
			methodDescriptor = lk.descriptor;
		}
	}

	public String getClassname() {
		return classname;
	}

	public String getMethodDescriptor() {
		return methodDescriptor;
	}

	public String getMethodName() {
		return mtname;
	}
	
	
}
