package org.jetbrains.java.decompiler.code.instructions;

import java.io.DataOutputStream;
import java.io.IOException;

import org.jetbrains.java.decompiler.code.JumpInstruction;

public class IFEQ extends JumpInstruction {

	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		out.writeByte(opc_ifeq);
		out.writeShort(getOperand(0));
	}	
	
	public int length() {
		return 3;
	}
	
}
