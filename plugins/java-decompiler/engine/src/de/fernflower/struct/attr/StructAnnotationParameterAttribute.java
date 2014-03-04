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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.fernflower.modules.decompiler.exps.AnnotationExprent;
import de.fernflower.struct.consts.ConstantPool;

public class StructAnnotationParameterAttribute extends StructGeneralAttribute {

	private List<List<AnnotationExprent>> paramAnnotations;
	
	public void initContent(ConstantPool pool) {

		super.initContent(pool);
		
		paramAnnotations = new ArrayList<List<AnnotationExprent>>();
		DataInputStream data = new DataInputStream(new ByteArrayInputStream(info));
		
		try {
			int len = data.readUnsignedByte();
			for(int i=0;i<len;i++) {
				List<AnnotationExprent> lst = new ArrayList<AnnotationExprent>(); 
				int annsize = data.readUnsignedShort();

				for(int j=0;j<annsize;j++) {
					lst.add(StructAnnotationAttribute.parseAnnotation(data, pool));
				}
				paramAnnotations.add(lst);
			}
		} catch(IOException ex) {
			throw new RuntimeException(ex);
		}

	}

	public List<List<AnnotationExprent>> getParamAnnotations() {
		return paramAnnotations;
	}
}
