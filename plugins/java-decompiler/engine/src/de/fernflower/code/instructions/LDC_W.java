package de.fernflower.code.instructions;

import java.io.DataOutputStream;
import java.io.IOException;

import de.fernflower.code.Instruction;

public class LDC_W extends Instruction {

	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		out.writeByte(opc_ldc_w);
		out.writeShort(getOperand(0));
	}
	
	public int length() {
		return 3;
	}

}
