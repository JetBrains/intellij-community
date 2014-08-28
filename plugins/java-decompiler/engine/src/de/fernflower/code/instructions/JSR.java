package de.fernflower.code.instructions;

import java.io.DataOutputStream;
import java.io.IOException;

import de.fernflower.code.JumpInstruction;

public class JSR extends JumpInstruction {

	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		int operand = getOperand(0);
		if(operand < -32768 || operand > 32767) {
			out.writeByte(opc_jsr_w);
			out.writeInt(operand);
		} else {
			out.writeByte(opc_jsr);
			out.writeShort(operand);
		}
	}
	
	public int length() {
		int operand = getOperand(0);
		if(operand < -32768 || operand > 32767) {
			return 5;
		} else {
			return 3;
		}
	}
	
}
