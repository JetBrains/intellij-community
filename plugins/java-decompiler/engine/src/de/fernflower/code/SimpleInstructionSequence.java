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

import org.jetbrains.java.decompiler.util.VBStyleCollection;

public class SimpleInstructionSequence extends InstructionSequence {

	public SimpleInstructionSequence() {}

	public SimpleInstructionSequence(VBStyleCollection<Instruction, Integer> collinstr) {
		this.collinstr = collinstr; 
	}
	
	public SimpleInstructionSequence clone() {
		SimpleInstructionSequence newseq = new SimpleInstructionSequence(collinstr.clone());
		newseq.setPointer(this.getPointer());
		
		return newseq; 
	}

	public void removeInstruction(int index) {
		collinstr.remove(index); 
	}
	
	
}
