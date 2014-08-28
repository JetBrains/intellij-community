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

package org.jetbrains.java.decompiler.struct.consts;

public interface VariableTypeEnum {

	public final static int BOOLEAN = 1;
	public final static int BYTE = 2;
	public final static int CHAR = 3;
	public final static int SHORT = 4;
	public final static int INT = 5;
	public final static int FLOAT = 6;
	public final static int LONG = 7;
	public final static int DOUBLE = 8;
	public final static int RETURN_ADDRESS = 9;
	public final static int REFERENCE = 10;
	public final static int INSTANCE_UNINITIALIZED = 11;
	public final static int VALUE_UNKNOWN = 12;
	public final static int VOID = 13;
	
	public final static Integer BOOLEAN_OBJ = new Integer(BOOLEAN);
	public final static Integer BYTE_OBJ = new Integer(BYTE);
	public final static Integer CHAR_OBJ = new Integer(CHAR);
	public final static Integer SHORT_OBJ = new Integer(SHORT);
	public final static Integer INT_OBJ = new Integer(INT);
	public final static Integer FLOAT_OBJ = new Integer(FLOAT);
	public final static Integer LONG_OBJ = new Integer(LONG);
	public final static Integer DOUBLE_OBJ = new Integer(DOUBLE);
	public final static Integer RETURN_ADDRESS_OBJ = new Integer(RETURN_ADDRESS);
	public final static Integer REFERENCE_OBJ = new Integer(REFERENCE);
	public final static Integer INSTANCE_UNINITIALIZED_OBJ = new Integer(INSTANCE_UNINITIALIZED);
	public final static Integer VALUE_UNKNOWN_OBJ = new Integer(VALUE_UNKNOWN);
	public final static Integer VOID_OBJ = new Integer(VOID);
	
}
