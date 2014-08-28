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

import de.fernflower.struct.consts.ConstantPool;

public class StructGenericSignatureAttribute extends StructGeneralAttribute {

	private String signature;

	public void initContent(ConstantPool pool) {

		name = ATTRIBUTE_SIGNATURE;
		signature = pool.getPrimitiveConstant(((info[0] & 0xFF)<<8) | (info[1] & 0xFF)).getString();
	}
	
	public String getSignature() {
		return signature;
	}
	
	
}
