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

package org.jetbrains.java.decompiler.code;

/*
 * opc_ifeq, opc_ifne, opc_iflt, opc_ifge, opc_ifgt, opc_ifle, opc_if_icmpeq, opc_if_icmpne, opc_if_icmplt,
 * opc_if_icmpge, opc_if_icmpgt, opc_if_icmple, opc_if_acmpeq, opc_if_acmpne, opc_ifnull, opc_ifnonnull
 * opc_goto, opc_jsr, opc_goto_w, opc_jsr_w
 */


public class JumpInstruction extends Instruction {

	public int destination;
	
	public JumpInstruction() {}
	
	public void initInstruction(InstructionSequence seq) {
		destination = seq.getPointerByRelOffset(this.getOperand(0));
	}

	public JumpInstruction clone() {
		JumpInstruction newinstr = (JumpInstruction)super.clone();
		
		newinstr.destination = destination;
		return newinstr;
	}
	
}
