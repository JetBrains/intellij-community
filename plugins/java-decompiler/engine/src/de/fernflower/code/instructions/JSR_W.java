package de.fernflower.code.instructions;

import java.io.DataOutputStream;
import java.io.IOException;

import de.fernflower.code.JumpInstruction;

public class JSR_W extends JumpInstruction {

	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		out.writeByte(opc_jsr_w);
		out.writeInt(getOperand(0));
	}
	
	public int length() {
		return 5;
	}
	
}
