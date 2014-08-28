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

package de.fernflower.code;

import de.fernflower.util.VBStyleCollection;


public class FullInstructionSequence extends InstructionSequence {

	// *****************************************************************************
	// constructors
	// *****************************************************************************
	
	public FullInstructionSequence(VBStyleCollection<Instruction, Integer> collinstr, ExceptionTable extable) {
		this.collinstr = collinstr; 
		this.exceptionTable = extable;
		
		// translate raw exception handlers to instr
		for(ExceptionHandler handler : extable.getHandlers()) {
			handler.from_instr = this.getPointerByAbsOffset(handler.from);
			handler.to_instr = this.getPointerByAbsOffset(handler.to);
			handler.handler_instr = this.getPointerByAbsOffset(handler.handler);
		}
	}

}
