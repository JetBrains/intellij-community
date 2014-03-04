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

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.fernflower.code.interpreter.Util;
import de.fernflower.main.DecompilerContext;
import de.fernflower.struct.StructContext;
import de.fernflower.util.InterpreterUtil;
import de.fernflower.util.VBStyleCollection;


public abstract class InstructionSequence {

	// *****************************************************************************
	// private fields
	// *****************************************************************************
	
	protected VBStyleCollection<Instruction, Integer> collinstr = new VBStyleCollection<Instruction, Integer>(); 
	
	protected int pointer = 0;
	
	protected ExceptionTable exceptionTable = new ExceptionTable(); 
	
	// *****************************************************************************
	// public methods
	// *****************************************************************************

	// to nbe overwritten
	public InstructionSequence clone() {return null;}
	
	public void clear() {
		collinstr.clear();
		pointer = 0;
		exceptionTable = new ExceptionTable(); 
	}
	
	public void addInstruction(Instruction inst, int offset){
		collinstr.addWithKey(inst, offset);
	}

	public void addInstruction(int index, Instruction inst, int offset){
		collinstr.addWithKeyAndIndex(index, inst, offset);
	}
	
	public void addSequence(InstructionSequence seq){
		for(int i=0;i<seq.length();i++) {
			addInstruction(seq.getInstr(i), -1); // TODO: any sensible value possible?
		}
	}
	
	public void removeInstruction(int index) {
		collinstr.remove(index);
	}
	
	public Instruction getCurrentInstr() {
		return (Instruction)collinstr.get(pointer);
	}
	
	public Instruction getInstr(int index) {
		return (Instruction)collinstr.get(index);
	}

	public Instruction getLastInstr() {
		return (Instruction)collinstr.getLast();
	}
	
	public int getCurrentOffset() {
		return ((Integer)collinstr.getKey(pointer)).intValue(); 
	}

	public int getOffset(int index) {
		return ((Integer)collinstr.getKey(index)).intValue(); 
	}
	
	public int getPointerByAbsOffset(int offset) {
		Integer absoffset = new Integer(offset);
		if(collinstr.containsKey(absoffset)) {
			return collinstr.getIndexByKey(absoffset);
		} else {
			return -1;
		}
	}

	public int getPointerByRelOffset(int offset) {
		Integer absoffset = new Integer(((Integer)collinstr.getKey(pointer)).intValue()+offset);
		if(collinstr.containsKey(absoffset)) {
			return collinstr.getIndexByKey(absoffset);
		} else {
			return -1;
		}
	}
	
	public void setPointerByAbsOffset(int offset) {
		Integer absoffset = new Integer(((Integer)collinstr.getKey(pointer)).intValue()+offset);
		if(collinstr.containsKey(absoffset)) {
			pointer = collinstr.getIndexByKey(absoffset);
		}
	}
	
	public int length() {
		return collinstr.size();  
	}
	
	public boolean isEmpty() {
		return collinstr.isEmpty();
	}
	
	public void addToPointer(int diff) {
		this.pointer += diff;
	}
	
	public String toString() {
		return toString(0); 
	}

	public String toString(int indent) {
		
		String new_line_separator = DecompilerContext.getNewLineSeparator();
		
		StringBuffer buf = new  StringBuffer();
		
		for(int i=0;i<collinstr.size();i++) {
			buf.append(InterpreterUtil.getIndentString(indent));
			buf.append(((Integer)collinstr.getKey(i)).intValue());
			buf.append(": ");
			buf.append(((Instruction)collinstr.get(i)).toString());
			buf.append(new_line_separator);
		}
		
		return buf.toString(); 
	}
	
	public void writeCodeToStream(DataOutputStream out) throws IOException {

		for(int i=0;i<collinstr.size();i++) {
			((Instruction)collinstr.get(i)).writeToStream(out, ((Integer)collinstr.getKey(i)).intValue());
		}
	}

	public void writeExceptionsToStream(DataOutputStream out) throws IOException {

		List<ExceptionHandler> handlers = exceptionTable.getHandlers();  
		
		out.writeShort(handlers.size());
		for(int i=0;i<handlers.size();i++) {
			((ExceptionHandler)handlers.get(i)).writeToStream(out);
		}
	}
	
	public void sortHandlers(final StructContext context) {
		
		Collections.sort(exceptionTable.getHandlers(), new Comparator<ExceptionHandler>() {

			public int compare(ExceptionHandler handler0, ExceptionHandler handler1) {
				
				if(handler0.to == handler1.to) {
					if(handler0.exceptionClass == null) {
						return 1;
					} else {
						if(handler1.exceptionClass == null) {
							return -1;
						} else if(handler0.exceptionClass.equals(handler1.exceptionClass)){
							return (handler0.from > handler1.from)?-1:1; // invalid code
						} else {
							if(Util.instanceOf(context, handler0.exceptionClass, handler1.exceptionClass)) {
								return -1;
							} else {
								return 1;
							}
						}
					}
				} else {
					return (handler0.to > handler1.to)?1:-1;
				}
			}
		}); 
		
	}
	
	
	// *****************************************************************************
	// getter and setter methods
	// *****************************************************************************

	public int getPointer() {
		return pointer;
	}

	public void setPointer(int pointer) {
		this.pointer = pointer;
	}
	
	public ExceptionTable getExceptionTable() {
		return exceptionTable;
	}

	public void setExceptionTable(ExceptionTable exceptionTable) {
		this.exceptionTable = exceptionTable;
	}
	
}
