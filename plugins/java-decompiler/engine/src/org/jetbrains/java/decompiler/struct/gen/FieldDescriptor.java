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

package org.jetbrains.java.decompiler.struct.gen;

public class FieldDescriptor {
	
	public static final FieldDescriptor INTEGER_DESCRIPTOR = FieldDescriptor.parseDescriptor("Ljava/lang/Integer;");
	public static final FieldDescriptor LONG_DESCRIPTOR = FieldDescriptor.parseDescriptor("Ljava/lang/Long;");
	public static final FieldDescriptor FLOAT_DESCRIPTOR = FieldDescriptor.parseDescriptor("Ljava/lang/Float;");
	public static final FieldDescriptor DOUBLE_DESCRIPTOR = FieldDescriptor.parseDescriptor("Ljava/lang/Double;");

	public VarType type;
	
	public String descriptorString;
	
	private FieldDescriptor() {}
	
	public static FieldDescriptor parseDescriptor(String descr) {
		
		FieldDescriptor fd = new FieldDescriptor(); 
		
		fd.type = new VarType(descr); 
		fd.descriptorString = descr;
		
		return fd;
	}
	
	public String getDescriptor() {
		return type.toString();  
	}

	@Override
	public boolean equals(Object o) {
    if(o == this) return true;
    if(o == null || !(o instanceof FieldDescriptor)) return false;

    FieldDescriptor fd = (FieldDescriptor)o;
    return type.equals(fd.type);
  }

	@Override
	public int hashCode() {
		return type.hashCode();
	}
	
}
