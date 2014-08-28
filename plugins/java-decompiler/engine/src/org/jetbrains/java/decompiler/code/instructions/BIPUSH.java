package org.jetbrains.java.decompiler.code.instructions;

import java.io.DataOutputStream;
import java.io.IOException;

import org.jetbrains.java.decompiler.code.Instruction;

public class BIPUSH extends Instruction {

	private static int[] opcodes = new int[] {opc_iconst_m1,opc_iconst_0,opc_iconst_1,opc_iconst_2,opc_iconst_3,opc_iconst_4,opc_iconst_5}; 
	
	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		int value = getOperand(0);
		if(value<-1 || value > 5) {
			out.writeByte(opc_bipush);
			out.writeByte(value);
		} else {
			out.writeByte(opcodes[value+1]);
		}
	}
	
	public int length() {
		int value = getOperand(0);
		if(value<-1 || value > 5) {
			return 2;
		} else {
			return 1;
		}
	}
}
