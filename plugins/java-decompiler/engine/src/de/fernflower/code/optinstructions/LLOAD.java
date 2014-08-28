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

public class LLOAD extends Instruction {

	private static int[] opcodes = new int[] {opc_lload_0,opc_lload_1,opc_lload_2,opc_lload_3}; 
	
	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		int index = getOperand(0);
		if(index>3) {
			if(wide) {
				out.writeByte(opc_wide);
			}
			out.writeByte(opc_lload);
			if(wide) {
				out.writeShort(index);
			} else {
				out.writeByte(index);
			}
		} else {
			out.writeByte(opcodes[index]);
		}
	}

	public int length() {
		int index = getOperand(0);
		if(index>3) {
			return wide?4:2;
		} else {
			return 1;
		}
	}
	
}
