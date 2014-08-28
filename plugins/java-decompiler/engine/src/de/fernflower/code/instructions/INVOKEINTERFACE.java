package de.fernflower.code.instructions;

import java.io.DataOutputStream;
import java.io.IOException;

import de.fernflower.code.Instruction;

public class INVOKEINTERFACE extends Instruction {

	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		out.writeByte(opc_invokeinterface);
		out.writeShort(getOperand(0));
		out.writeByte(getOperand(1));
		out.writeByte(0);
	}
	
	public int length() {
		return 5;
	}
	
}
