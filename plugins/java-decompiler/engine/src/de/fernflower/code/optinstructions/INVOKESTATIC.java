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
