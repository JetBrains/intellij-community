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

package de.fernflower.struct;

import java.io.DataOutputStream;
import java.io.IOException;

import de.fernflower.struct.attr.StructGeneralAttribute;
import de.fernflower.struct.consts.ConstantPool;
import de.fernflower.util.VBStyleCollection;

/*
 	field_info {
    	u2 access_flags;
    	u2 name_index;
    	u2 descriptor_index;
    	u2 attributes_count;
    	attribute_info attributes[attributes_count];
    }
*/

public class StructField {

	// *****************************************************************************
	// public fields
	// *****************************************************************************
	
	public int access_flags;
	public int name_index;
	public int descriptor_index;
	
	private String name;
	private String descriptor;
	
	// *****************************************************************************
	// private fields
	// *****************************************************************************
	
	private VBStyleCollection<StructGeneralAttribute, String> attributes;

	// *****************************************************************************
	// constructors
	// *****************************************************************************
	
	public StructField() {}
	
	// *****************************************************************************
	// public methods
	// *****************************************************************************
	
	public void writeToStream(DataOutputStream out) throws IOException {

		out.writeShort(access_flags);
		out.writeShort(name_index);
		out.writeShort(descriptor_index);
		
		out.writeShort(attributes.size());
		for(StructGeneralAttribute attr: attributes) {
			attr.writeToStream(out);
		}
	}
	
	public void initStrings(ConstantPool pool, int class_index) {
		String[] values = pool.getClassElement(ConstantPool.FIELD, class_index, name_index, descriptor_index); 
		name = values[0];
		descriptor = values[1];
	}
	
	// *****************************************************************************
	// getter and setter methods
	// *****************************************************************************
	
	public VBStyleCollection<StructGeneralAttribute, String> getAttributes() {
		return attributes;
	}

	public void setAttributes(VBStyleCollection<StructGeneralAttribute, String> attributes) {
		this.attributes = attributes;
	}

	public String getDescriptor() {
		return descriptor;
	}

	public void setDescriptor(String descriptor) {
		this.descriptor = descriptor;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
}
