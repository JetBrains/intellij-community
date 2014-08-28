/*
 *    Fernflower - The Analytical Java Decompiler
 *    http://www.reversed-java.com
 *
 *    (C) 2008 - 2010, Stiver
 *
 *    This software is NEITHER public domain NOR free software 
 *    as per GNU License. See license.txt for more details.
 *
 *    This software is distributed WITHOUT ANY WARRANTY; without 
 *    even the implied warranty of MERCHANTABILITY or FITNESS FOR 
 *    A PARTICULAR PURPOSE. 
 */

package org.jetbrains.java.decompiler.code.optinstructions;

import java.io.DataOutputStream;
import java.io.IOException;

import org.jetbrains.java.decompiler.code.JumpInstruction;

public class GOTO extends JumpInstruction {

	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		int operand = getOperand(0);
		if(operand < -32768 || operand > 32767) {
			out.writeByte(opc_goto_w);
			out.writeInt(operand);
		} else {
			out.writeByte(opc_goto);
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
