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

import de.fernflower.code.SwitchInstruction;

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
