package org.jetbrains.java.decompiler.code.instructions;

import java.io.DataOutputStream;
import java.io.IOException;

import org.jetbrains.java.decompiler.code.Instruction;

public class ILOAD extends Instruction {

	private static int[] opcodes = new int[] {opc_iload_0,opc_iload_1,opc_iload_2,opc_iload_3}; 
	
	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		int index = getOperand(0);
		if(index>3) {
			if(wide) {
				out.writeByte(opc_wide);
			}
			out.writeByte(opc_iload);
			if(wide) {
				out.writeShort(index);
			} else {
				out.writeByte(index);
			}
		} else {
			out.writeByte(opcodes[index]);
		}
	}
	
	public int length() {
		int index = getOperand(0);
		if(index>3) {
			return wide?4:2;
		} else {
			return 1;
		}
	}
	
}
