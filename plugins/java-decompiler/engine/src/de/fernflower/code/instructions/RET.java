package org.jetbrains.java.decompiler.code.instructions;

import java.io.DataOutputStream;
import java.io.IOException;

import org.jetbrains.java.decompiler.code.Instruction;

public class RET extends Instruction {

	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		if(wide) {
			out.writeByte(opc_wide);
		}
		out.writeByte(opc_ret);
		if(wide) {
			out.writeShort(getOperand(0));
		} else {
			out.writeByte(getOperand(0));
		}
	}
	
	public int length() {
		return wide?4:2;
	}
	
}
