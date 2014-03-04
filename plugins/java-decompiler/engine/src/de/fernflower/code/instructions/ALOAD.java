package de.fernflower.code.instructions;

import java.io.DataOutputStream;
import java.io.IOException;

import de.fernflower.code.Instruction;

public class ALOAD extends Instruction {
	
	private static int[] opcodes = new int[] {opc_aload_0,opc_aload_1,opc_aload_2,opc_aload_3}; 

	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		int index = getOperand(0);
		if(index>3) {
			if(wide) {
				out.writeByte(opc_wide);
			}
			out.writeByte(opc_aload);
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
			if(wide) {
				return 4; 
			} else {
				return 2;
			}
		} else {
			return 1;
		}
	}

	
}
