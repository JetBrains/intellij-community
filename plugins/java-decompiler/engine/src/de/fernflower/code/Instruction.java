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

package org.jetbrains.java.decompiler.code;

import java.io.DataOutputStream;
import java.io.IOException;

public class Instruction implements CodeConstants {

	// *****************************************************************************
	// public fields
	// *****************************************************************************
	
	public int opcode;
	
	public int group = CodeConstants.GROUP_GENERAL;
	
	public boolean wide = false;
	
	public int bytecode_version = BYTECODE_JAVA_LE_4;
	
	// *****************************************************************************
	// private fields
	// *****************************************************************************
	
	private int[] operands = null;
	
	// *****************************************************************************
	// public methods
	// *****************************************************************************

	public Instruction() {}
	
	public int length() {
		return 1;
	}
	
	public int operandsCount() {
		return (operands==null)?0:operands.length; 
	}
	
	public int getOperand(int index) {
		return operands[index];
	}
	
	public Instruction clone() {
		return ConstantsUtil.getInstructionInstance(opcode, wide, group, bytecode_version, operands==null?null:(int[])operands.clone());
	}
	
	public String toString() {
		
		String res = wide?"@wide ":"";
		res+="@"+ConstantsUtil.getName(opcode); 

		int len = operandsCount();
		for(int i=0;i<len;i++) {
			int op = operands[i];
			if(op<0) {
				res+=" -"+Integer.toHexString(-op);
			} else {
				res+=" "+Integer.toHexString(op);
			}
		}
		
		return res;
		
	}
	
	public boolean canFallthrough() {
		return opcode!=opc_goto && opcode!=opc_goto_w && opcode!=opc_ret && 
		!(opcode>=opc_ireturn && opcode<=opc_return) && opcode!=opc_athrow
		 && opcode!=opc_jsr && opcode!=opc_tableswitch && opcode!=opc_lookupswitch; 
	}

	public boolean equalsInstruction(Instruction instr) {
		if(opcode != instr.opcode || wide != instr.wide
				|| operandsCount() != instr.operandsCount()) {
			return false;
		}
		
		if(operands != null) {
			for(int i=0;i<operands.length;i++) {
				if(operands[i] != instr.getOperand(i)) {
					return false;
				}
			}
		}
		
		return true;
	}
	
	// should be overwritten by subclasses
	public void initInstruction(InstructionSequence seq) {}

	// should be overwritten by subclasses
	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		out.writeByte(opcode);
	}
	
	// *****************************************************************************
	// getter and setter methods
	// *****************************************************************************
	
	public int[] getOperands() {
		return operands;
	}

	public void setOperands(int[] operands) {
		this.operands = operands;
	}
	
}
