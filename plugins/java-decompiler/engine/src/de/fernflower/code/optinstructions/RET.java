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

package de.fernflower.code.optinstructions;

import java.io.DataOutputStream;
import java.io.IOException;

import de.fernflower.code.Instruction;

public class RET extends Instruction {

	public void writeToStream(DataOutputStream out, int offset) throws IOException {
		if(wide) {
			out.writeByte(opc_wide);
		}
		out.writeByte(opc_ret);
		if(wide) {
			out.writeShort(getOperand(0));
		} else {
			out.writeByte(getOperand(0));
		}
	}
	
	public int length() {
		return wide?4:2;
	}
	
}
