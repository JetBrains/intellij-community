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

package de.fernflower.struct.consts;

import java.io.DataOutputStream;
import java.io.IOException;

import de.fernflower.code.CodeConstants;

/*
    cp_info {
    	u1 tag;
    	u1 info[];
    }
    
*/

public class PooledConstant implements CodeConstants, VariableTypeEnum {

	// *****************************************************************************
	// public fields
	// *****************************************************************************
	
	public int type;

	public boolean own = false;
	
	public int returnType;


	// *****************************************************************************
	// private fields
	// *****************************************************************************

	private Object[] values;

	// *****************************************************************************
	// constructors
	// *****************************************************************************
	
	public PooledConstant() {}
	
	public PooledConstant(int type, Object[] values) {
		this.type = type;
		this.values = values;
		this.returnType = poolTypeToIntern(type);
	}
	
	public PooledConstant(int type, boolean own, Object[] values) {
		this.type = type;
		this.own = own;
		this.values = values;
		this.returnType = poolTypeToIntern(type);
	}
	
	// *****************************************************************************
	// public methods
	// *****************************************************************************
	
	public void resolveConstant(ConstantPool pool) {
		// to be overwritten
	}
	
	public void writeToStream(DataOutputStream out) throws IOException {
		// to be overwritten
	}
	
	public int poolTypeToIntern(int type) {
		
		switch(type){
		case CONSTANT_Integer:
			return INT;
		case CONSTANT_Float:
			return FLOAT;
		case CONSTANT_Long:
			return LONG;
		case CONSTANT_Double:
			return DOUBLE;
		case CONSTANT_String:
		case CONSTANT_Class:  // 1.5 -> ldc class    
			return REFERENCE;
		default:
			throw new RuntimeException("Huh?? What are you trying to load?");
		}
	}

	public Object getValue(int index){
		return values[index];
	}
	
	
	// *****************************************************************************
	// getter and setter methods
	// *****************************************************************************
	
	public Object[] getValues() {
		return values;
	}

	public void setValues(Object[] values) {
		this.values = values;
	}

}
