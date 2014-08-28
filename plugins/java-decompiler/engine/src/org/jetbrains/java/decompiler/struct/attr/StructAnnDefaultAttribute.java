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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;


public class StructAnnDefaultAttribute extends StructGeneralAttribute {

	private Exprent defaultValue;
	
	public void initContent(ConstantPool pool) {

		name = ATTRIBUTE_ANNOTATION_DEFAULT;
	
		DataInputStream data = new DataInputStream(new ByteArrayInputStream(info));
		defaultValue = StructAnnotationAttribute.parseAnnotationElement(data, pool); 
	}
	
	public Exprent getDefaultValue() {
		return defaultValue;
	}
	
}
