package org.jetbrains.java.decompiler.code.instructions;

import java.io.DataOutputStream;
import java.io.IOException;

import org.jetbrains.java.decompiler.code.SwitchInstruction;

public class LOOKUPSWITCH extends SwitchInstruction {

	public void writeToStream(DataOutputStream out, int offset) throws IOException {

		out.writeByte(opc_lookupswitch);
		
		int padding = 3 - (offset%4);
		for(int i=0;i<padding;i++){
			out.writeByte(0);
		}
		
		for(int i=0;i<operandsCount();i++) {
			out.writeInt(getOperand(i));
		}
	}

	public int length() {
		return 1+operandsCount()*4;
	}
	
}
