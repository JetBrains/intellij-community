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

package de.fernflower.modules.decompiler.stats;

import de.fernflower.code.CodeConstants;
import de.fernflower.code.Instruction;
import de.fernflower.code.SimpleInstructionSequence;
import de.fernflower.code.cfg.BasicBlock;
import de.fernflower.main.DecompilerContext;
import de.fernflower.main.collectors.CounterContainer;
import de.fernflower.modules.decompiler.ExprProcessor;

public class BasicBlockStatement extends Statement {
	
	// *****************************************************************************
	// private fields
	// *****************************************************************************
	
	private BasicBlock block;
	
	// *****************************************************************************
	// constructors
	// *****************************************************************************
	
	public BasicBlockStatement(BasicBlock block) {
		
		type = Statement.TYPE_BASICBLOCK;  

		this.block = block;
		
		id = block.id;
		CounterContainer coun = DecompilerContext.getCountercontainer(); 
		if(id>=coun.getCounter(CounterContainer.STATEMENT_COUNTER)) {
			coun.setCounter(CounterContainer.STATEMENT_COUNTER, id+1);
		}
		
		Instruction instr = block.getLastInstruction();
		if(instr != null) {
			if(instr.group==CodeConstants.GROUP_JUMP && instr.opcode != CodeConstants.opc_goto) {
				lastBasicType = LASTBASICTYPE_IF; 
			} else if(instr.group==CodeConstants.GROUP_SWITCH) {
				lastBasicType = LASTBASICTYPE_SWITCH; 
			}
		}
		
		// monitorenter and monitorexits
		buildMonitorFlags(); 
	}

	// *****************************************************************************
	// public methods
	// *****************************************************************************

	public String toJava(int indent) {
		return ExprProcessor.listToJava(varDefinitions, indent)+
				ExprProcessor.listToJava(exprents, indent);
	}
	
	public Statement getSimpleCopy() {
		
		BasicBlock newblock = new BasicBlock(
				DecompilerContext.getCountercontainer().getCounterAndIncrement(CounterContainer.STATEMENT_COUNTER));
		
		SimpleInstructionSequence seq = new SimpleInstructionSequence();
		for(int i=0;i<block.getSeq().length();i++) {
			seq.addInstruction(block.getSeq().getInstr(i).clone(), -1);
		}
		
		newblock.setSeq(seq);
		
		return new BasicBlockStatement(newblock);
	}

	
	// *****************************************************************************
	// getter and setter methods
	// *****************************************************************************

	public BasicBlock getBlock() {
		return block;
	}
	
}
