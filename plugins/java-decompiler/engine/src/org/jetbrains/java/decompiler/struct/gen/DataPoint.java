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

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.util.ListStack;

public class DataPoint {

	private List<VarType> localVariables = new ArrayList<VarType>();
	
	private ListStack<VarType> stack = new ListStack<VarType>();

	
	public void setVariable(int index, VarType value) {
		if(index>=localVariables.size()) {
			for(int i=localVariables.size();i<=index;i++) {
				localVariables.add(new VarType(CodeConstants.TYPE_NOTINITIALIZED));
			}
		}
		
		localVariables.set(index, value); 
	}
	
	public VarType getVariable(int index) {
		if(index<localVariables.size()) {
			return localVariables.get(index);
		} else if(index<0) {
			throw new IndexOutOfBoundsException();
		} else {
			return new VarType(CodeConstants.TYPE_NOTINITIALIZED);
		}
	}
	
	public DataPoint copy() {
		DataPoint point = new DataPoint();
		point.setLocalVariables(new ArrayList<VarType>(localVariables));
		point.setStack(stack.clone());
		return point;
	}
	
	public static DataPoint getInitialDataPoint(StructMethod mt) {
		
		DataPoint point = new DataPoint(); 
		
		MethodDescriptor md = MethodDescriptor.parseDescriptor(mt.getDescriptor());
		
		int k = 0;
		if((mt.getAccessFlags() & CodeConstants.ACC_STATIC) == 0) {
			point.setVariable(k++, new VarType(CodeConstants.TYPE_OBJECT, 0, null));
		}
		
		for(int i=0;i<md.params.length;i++) {
			VarType var = md.params[i];
			
			point.setVariable(k++, var);
			if(var.stack_size == 2) {
				point.setVariable(k++, new VarType(CodeConstants.TYPE_GROUP2EMPTY));
			}
		}
		
		return point;
	}
	
	
	public List<VarType> getLocalVariables() {
		return localVariables;
	}

	public void setLocalVariables(List<VarType> localVariables) {
		this.localVariables = localVariables;
	}

	public ListStack<VarType> getStack() {
		return stack;
	}

	public void setStack(ListStack<VarType> stack) {
		this.stack = stack;
	}
	
}
