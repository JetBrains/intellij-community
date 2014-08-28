package org.jetbrains.java.decompiler.code.instructions;

import java.io.DataOutputStream;
import java.io.IOException;

import org.jetbrains.java.decompiler.code.Instruction;

public class INVOKESTATIC extends Instruction {

	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		out.writeByte(opc_invokestatic);
		out.writeShort(getOperand(0));
	}
	
	public int length() {
		return 3;
	}
	
}
