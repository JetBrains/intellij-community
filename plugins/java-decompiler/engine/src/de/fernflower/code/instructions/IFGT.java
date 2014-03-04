package de.fernflower.code.instructions;

import java.io.DataOutputStream;
import java.io.IOException;

import de.fernflower.code.JumpInstruction;

public class IFGT extends JumpInstruction {

	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		out.writeByte(opc_ifgt);
		out.writeShort(getOperand(0));
	}	

	public int length() {
		return 3;
	}
	
}
