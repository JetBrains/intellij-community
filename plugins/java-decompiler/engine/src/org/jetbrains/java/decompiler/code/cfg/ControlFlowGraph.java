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

package org.jetbrains.java.decompiler.code.cfg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.ExceptionHandler;
import org.jetbrains.java.decompiler.code.Instruction;
import org.jetbrains.java.decompiler.code.InstructionSequence;
import org.jetbrains.java.decompiler.code.JumpInstruction;
import org.jetbrains.java.decompiler.code.SimpleInstructionSequence;
import org.jetbrains.java.decompiler.code.SwitchInstruction;
import org.jetbrains.java.decompiler.code.interpreter.InstructionImpact;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.code.DeadCodeHelper;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.struct.gen.DataPoint;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.ListStack;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

public class ControlFlowGraph implements CodeConstants {

	public int last_id = 0; 
	
	// *****************************************************************************
	// private fields
	// *****************************************************************************
	
	private VBStyleCollection<BasicBlock, Integer> blocks;
	
	private BasicBlock first;
	
	private BasicBlock last;

	private List<ExceptionRangeCFG> exceptions;
	
	private HashMap<BasicBlock, BasicBlock> subroutines;
	
	private HashSet<BasicBlock> finallyExits = new HashSet<BasicBlock>();

	// *****************************************************************************
	// constructors
	// *****************************************************************************
	
	public ControlFlowGraph(InstructionSequence seq) {
		buildBlocks(seq);
	}
	
	
	// *****************************************************************************
	// public methods
	// *****************************************************************************
	
	public void free() {
		
		for(BasicBlock block: blocks) {
			block.free();
		}
		
		blocks.clear(); 
		first = null;
		last = null;
		exceptions.clear();
		finallyExits.clear();
	}
	
	public void removeMarkers() {
		for(BasicBlock block: blocks) {
			block.mark = 0;
		}
	}
	
	public String toString() {
		
		String new_line_separator = DecompilerContext.getNewLineSeparator();
		
		StringBuffer buf = new StringBuffer(); 
		
		for(BasicBlock block: blocks) {
			buf.append("----- Block "+block.id+" -----" + new_line_separator);
			buf.append(block.toString());
			buf.append("----- Edges -----" + new_line_separator);
			
			List<BasicBlock> suc = block.getSuccs();
			for(int j=0;j<suc.size();j++) {
				buf.append(">>>>>>>>(regular) Block "+((BasicBlock)suc.get(j)).id+new_line_separator);
			}
			suc = block.getSuccExceptions();
			for(int j=0;j<suc.size();j++) {
				BasicBlock handler = (BasicBlock)suc.get(j);
				ExceptionRangeCFG range = getExceptionRange(handler, block);
				
				if(range == null) {
					buf.append(">>>>>>>>(exception) Block "+handler.id+"\t"+"ERROR: range not found!"+new_line_separator);
				} else {
					List<String> exceptionTypes = range.getExceptionTypes();
					if(exceptionTypes == null) {
						buf.append(">>>>>>>>(exception) Block "+handler.id+"\t"+"NULL"+new_line_separator);
					} else {
						for(String exceptionType : exceptionTypes) {
							buf.append(">>>>>>>>(exception) Block "+handler.id+"\t"+exceptionType+new_line_separator);
						}
					}
				}
			}
			buf.append("----- ----- -----" + new_line_separator);
			
		}
		
		return buf.toString(); 

	}
	
	public void inlineJsr(StructMethod mt) {
		processJsr();
		removeJsr(mt);
		
		removeMarkers();
		
		DeadCodeHelper.removeEmptyBlocks(this);
	}
	
	public void removeBlock(BasicBlock block) {
		
		while(block.getSuccs().size()>0) {
			block.removeSuccessor((BasicBlock)block.getSuccs().get(0));
		}
		
		while(block.getSuccExceptions().size()>0) {
			block.removeSuccessorException((BasicBlock)block.getSuccExceptions().get(0));
		}

		while(block.getPreds().size()>0) {
			((BasicBlock)block.getPreds().get(0)).removeSuccessor(block);
		}

		while(block.getPredExceptions().size()>0) {
			((BasicBlock)block.getPredExceptions().get(0)).removeSuccessorException(block);
		}

		last.removePredecessor(block);

		blocks.removeWithKey(block.id);
		
		for(int i=exceptions.size()-1;i>=0;i--) {
			ExceptionRangeCFG range = (ExceptionRangeCFG)exceptions.get(i);
			if(range.getHandler() == block) {
				exceptions.remove(i);
			} else {
				List<BasicBlock> lstRange = range.getProtectedRange();
				lstRange.remove(block);
				
				if(lstRange.isEmpty()) {
					exceptions.remove(i);
				}
			}
		}
		
		Iterator<Entry<BasicBlock, BasicBlock>> it = subroutines.entrySet().iterator();
		while(it.hasNext()) {
			Entry<BasicBlock, BasicBlock> ent = it.next();
			if(ent.getKey() == block || ent.getValue() == block) {
				it.remove(); 
			}
		}
		
	}
	
	public ExceptionRangeCFG getExceptionRange(BasicBlock handler, BasicBlock block) {
		
		//List<ExceptionRangeCFG> ranges = new ArrayList<ExceptionRangeCFG>();
		
		for(int i=exceptions.size()-1;i>=0;i--) {
			ExceptionRangeCFG range = exceptions.get(i);
			if(range.getHandler() == handler && range.getProtectedRange().contains(block)) {
				return range;
				//ranges.add(range);
			}
		}
		
		return null;
		//return ranges.isEmpty() ? null : ranges;
	}

//	public String getExceptionsUniqueString(BasicBlock handler, BasicBlock block) {
//		
//		List<ExceptionRangeCFG> ranges = getExceptionRange(handler, block);
//		
//		if(ranges == null) {
//			return null;
//		} else {
//			Set<String> setExceptionStrings = new HashSet<String>();
//			for(ExceptionRangeCFG range : ranges) {
//				setExceptionStrings.add(range.getExceptionType());
//			}
//			
//			String ret = "";
//			for(String exception : setExceptionStrings) {
//				ret += exception;
//			}
//			
//			return ret;
//		}
//	}
	
	
	// *****************************************************************************
	// private methods
	// *****************************************************************************
	
	private void buildBlocks(InstructionSequence instrseq) {
		
		short[] states = findStartInstructions(instrseq);
		
		HashMap<Integer, BasicBlock> mapInstrBlocks = new HashMap<Integer, BasicBlock>();
		VBStyleCollection<BasicBlock, Integer> colBlocks = createBasicBlocks(states, instrseq, mapInstrBlocks);

		blocks = colBlocks;

		connectBlocks(colBlocks, mapInstrBlocks);

		setExceptionEdges(instrseq, mapInstrBlocks);

		setSubroutineEdges();
		
		setFirstAndLastBlocks();
	}
	
	private short[] findStartInstructions(InstructionSequence seq) {
		
		int len = seq.length();
		short[] inststates = new short[len];
		
		HashSet<Integer> excSet = new HashSet<Integer>();

		for(ExceptionHandler handler : seq.getExceptionTable().getHandlers()) {
			excSet.add(handler.from_instr);
			excSet.add(handler.to_instr);
			excSet.add(handler.handler_instr);
		}
		
		
		for(int i=0;i<len;i++) {
			
			// exception blocks
			if(excSet.contains(new Integer(i))) {
				inststates[i] = 1;
			}
			
			Instruction instr = seq.getInstr(i);
			switch(instr.group){
			case GROUP_JUMP:
				inststates[((JumpInstruction)instr).destination] = 1;
			case GROUP_RETURN:
				if(i+1 < len) {
					inststates[i+1] = 1;
				}
				break;
			case GROUP_SWITCH:
				SwitchInstruction swinstr = (SwitchInstruction)instr;
				int[] dests = swinstr.getDestinations();
				for(int j=dests.length-1;j>=0;j--) {
					inststates[dests[j]] = 1;
				}
				inststates[swinstr.getDefaultdest()] = 1;
				if(i+1 < len) {
					inststates[i+1] = 1;
				}
			}
		}
		
		// first instruction
		inststates[0] = 1;
		
		return inststates; 
	}
	
	
	private VBStyleCollection<BasicBlock, Integer> createBasicBlocks(short[] startblock, InstructionSequence instrseq, 
			HashMap<Integer, BasicBlock> mapInstrBlocks) {

		VBStyleCollection<BasicBlock, Integer> col = new VBStyleCollection<BasicBlock, Integer>();
		
		InstructionSequence currseq = null;
		ArrayList<Integer> lstOffs = null;
		
		int len = startblock.length;
		short counter = 0;
		int blockoffset = 0;
		
		BasicBlock currentBlock = null;
		for(int i=0;i<len;i++) {
			
			if (startblock[i] == 1) {
				currentBlock = new BasicBlock();
				currentBlock.id = ++counter;
				
				currseq = new SimpleInstructionSequence();
				lstOffs = new ArrayList<Integer>();
				
				currentBlock.setSeq(currseq);
				currentBlock.setInstrOldOffsets(lstOffs);
				col.addWithKey(currentBlock, currentBlock.id);
				
				blockoffset = instrseq.getOffset(i);
			}

			startblock[i] = counter; 
			mapInstrBlocks.put(i, currentBlock);
			
			currseq.addInstruction(instrseq.getInstr(i), instrseq.getOffset(i)-blockoffset);
			lstOffs.add(instrseq.getOffset(i));
		}
		
		last_id = counter;
		
		return col;
	}

	
	private void connectBlocks(List<BasicBlock> lstbb, HashMap<Integer, BasicBlock> mapInstrBlocks) {
		
		for(int i=0;i<lstbb.size();i++) {
			
			BasicBlock block = lstbb.get(i);
			Instruction instr = block.getLastInstruction(); 
			
			boolean fallthrough = instr.canFallthrough();
			BasicBlock bTemp;
			
			switch(instr.group) {
			case GROUP_JUMP:
				int dest = ((JumpInstruction)instr).destination;	
				bTemp = mapInstrBlocks.get(dest);
				block.addSuccessor(bTemp);

				break;
			case GROUP_SWITCH:
				SwitchInstruction sinstr = (SwitchInstruction)instr;
				int[] dests = sinstr.getDestinations();

				bTemp = mapInstrBlocks.get(((SwitchInstruction)instr).getDefaultdest());
				block.addSuccessor(bTemp);
				for(int j=0;j<dests.length;j++) {
					bTemp = mapInstrBlocks.get(dests[j]);
					block.addSuccessor(bTemp);
				}
			}
			
			if(fallthrough && i<lstbb.size()-1) {
				BasicBlock defaultBlock = lstbb.get(i+1);
				block.addSuccessor(defaultBlock);
			}
		}
		
	}
	
	private void setExceptionEdges(InstructionSequence instrseq, HashMap<Integer, BasicBlock> instrBlocks) {
		
		exceptions = new ArrayList<ExceptionRangeCFG>(); 
		
		Map<String, ExceptionRangeCFG> mapRanges = new HashMap<String, ExceptionRangeCFG>(); 
		
		for(ExceptionHandler handler : instrseq.getExceptionTable().getHandlers()) {
			
			BasicBlock from = instrBlocks.get(handler.from_instr); 
			BasicBlock to = instrBlocks.get(handler.to_instr); 
			BasicBlock handle = instrBlocks.get(handler.handler_instr); 

			String key = from.id + ":" + to.id + ":" + handle.id;
			
			if(mapRanges.containsKey(key)) {
				ExceptionRangeCFG range = mapRanges.get(key);
				range.addExceptionType(handler.exceptionClass);
			} else {
			
				List<BasicBlock> protectedRange = new ArrayList<BasicBlock>();  
				for(int j=from.id;j<to.id;j++) {
					BasicBlock block = blocks.getWithKey(j);
					protectedRange.add(block);
					block.addSuccessorException(handle);
				}
				
				ExceptionRangeCFG range = new ExceptionRangeCFG(protectedRange, handle, handler.exceptionClass == null ? null : Arrays.asList(new String[]{handler.exceptionClass}));
				mapRanges.put(key, range);
				
				exceptions.add(range);
			}
		}
	}
	
	private void setSubroutineEdges() {
		
		final HashMap<BasicBlock, BasicBlock> subroutines = new HashMap<BasicBlock, BasicBlock>();
		
		for(BasicBlock block : blocks) {
			
			if(block.getSeq().getLastInstr().opcode == CodeConstants.opc_jsr) {

				LinkedList<BasicBlock> stack = new LinkedList<BasicBlock>();
				LinkedList<LinkedList<BasicBlock>> stackJsrStacks = new LinkedList<LinkedList<BasicBlock>>(); 
				
				HashSet<BasicBlock> setVisited = new HashSet<BasicBlock>();
				
				stack.add(block);
				stackJsrStacks.add(new LinkedList<BasicBlock>());
				
				while(!stack.isEmpty()) {
					
					BasicBlock node = stack.removeFirst();
					LinkedList<BasicBlock> jsrstack = stackJsrStacks.removeFirst();
					
					setVisited.add(node);
					
					switch(node.getSeq().getLastInstr().opcode) {
					case CodeConstants.opc_jsr:
						jsrstack.add(node);
						break;
					case CodeConstants.opc_ret:
						BasicBlock enter = jsrstack.getLast();
						BasicBlock exit = blocks.getWithKey(enter.id + 1); // FIXME: find successor in a better way

						if(exit!=null) {
							if(!node.isSuccessor(exit)) {
								node.addSuccessor(exit);
							}
							jsrstack.removeLast();
							subroutines.put(enter, exit);
						} else {
							throw new RuntimeException("ERROR: last instruction jsr");
						}
					}
					
					if(!jsrstack.isEmpty()) {
						for(BasicBlock succ : node.getSuccs()) {
							if(!setVisited.contains(succ)) {
								stack.add(succ);
								stackJsrStacks.add(new LinkedList<BasicBlock>(jsrstack));
							}
						}
					}
				}
			}
		}

		this.subroutines = subroutines; 
	}
	
	private void processJsr() {
		
		while(processJsrRanges()!=0);
	}
	
	private int processJsrRanges() {
		
		List<Object[]> lstJsrAll = new ArrayList<Object[]>();
		
		// get all jsr ranges
		for(Entry<BasicBlock, BasicBlock> ent : subroutines.entrySet()){
			BasicBlock jsr = ent.getKey();
			BasicBlock ret = ent.getValue();
			
			lstJsrAll.add(new Object[]{jsr, getJsrRange(jsr, ret), ret});
		}
		
		// sort ranges
		// FIXME: better sort order
		List<Object[]> lstJsr = new ArrayList<Object[]>();
		for(Object[] arr : lstJsrAll) {
			int i=0;
			for(;i<lstJsr.size();i++) {
				Object[] arrJsr = lstJsr.get(i);
				
				if(((HashSet<BasicBlock>)arrJsr[1]).contains(arr[0])) {
					break;
				}
			}
			
			lstJsr.add(i, arr);
		}
		
		// find the first intersection
		for(int i=0;i<lstJsr.size();i++) {
			Object[] arr = (Object[])lstJsr.get(i);
			HashSet<BasicBlock> set = (HashSet<BasicBlock>)arr[1];

			for(int j=i+1;j<lstJsr.size();j++) {
				Object[] arr1 = (Object[])lstJsr.get(j);
				HashSet<BasicBlock> set1 = (HashSet<BasicBlock>)arr1[1];

				if(!set.contains(arr1[0]) && !set1.contains(arr[0])) { // rang 0 doesn't contain entry 1 and vice versa 
					HashSet<BasicBlock> setc = new HashSet<BasicBlock>(set);
					setc.retainAll(set1);
					
					if(!setc.isEmpty()) {
						splitJsrRange((BasicBlock)arr[0], (BasicBlock)arr[2], setc);
						return 1;
					}
				}
			}
		}
		
		return 0;
	}

	private HashSet<BasicBlock> getJsrRange(BasicBlock jsr, BasicBlock ret) {

		HashSet<BasicBlock> blocks = new HashSet<BasicBlock>();
		
		LinkedList<BasicBlock> lstNodes = new LinkedList<BasicBlock>();
		lstNodes.add(jsr);
		
		BasicBlock dom = jsr.getSuccs().get(0); 

		while(!lstNodes.isEmpty()) {
			
			BasicBlock node = lstNodes.remove(0); 

			for(int j=0;j<2;j++) {
				List<BasicBlock> lst;
				if(j==0) {
					if(node.getLastInstruction().opcode == CodeConstants.opc_ret) {
						if(node.getSuccs().contains(ret)) {
							continue;
						}
					}
					lst = node.getSuccs(); 
				} else {
					if(node == jsr) {
						continue;
					}
					lst = node.getSuccExceptions(); 
				}
				
				CHILD:
				for(int i=lst.size()-1;i>=0;i--) {
					
					BasicBlock child = lst.get(i);
					if(!blocks.contains(child)) {
						
						if(node != jsr) {
							for(int k=0;k<child.getPreds().size();k++) {
								if(!DeadCodeHelper.isDominator(this, child.getPreds().get(k), dom)) {
									continue CHILD;
								}
							}
							
							for(int k=0;k<child.getPredExceptions().size();k++) {
								if(!DeadCodeHelper.isDominator(this, child.getPredExceptions().get(k), dom)) {
									continue CHILD;
								}
							}
						}

						// last block is a dummy one
						if(child!=last) {
							blocks.add(child);
						}
						
						lstNodes.add(child);
					}
				}
			}
		}
		
		return blocks;
	}
	
	private void splitJsrRange(BasicBlock jsr, BasicBlock ret, HashSet<BasicBlock> common_blocks) {

		LinkedList<BasicBlock> lstNodes = new LinkedList<BasicBlock>();
		HashMap<Integer, BasicBlock> mapNewNodes = new HashMap<Integer, BasicBlock>();
		
		lstNodes.add(jsr);
		mapNewNodes.put(jsr.id, jsr);
		
		while(!lstNodes.isEmpty()) {

			BasicBlock node = lstNodes.remove(0); 

			for(int j=0;j<2;j++) {
				List<BasicBlock> lst;
				if(j==0) {
					if(node.getLastInstruction().opcode == CodeConstants.opc_ret) {
						if(node.getSuccs().contains(ret)) {
							continue;
						}
					}
					lst = node.getSuccs(); 
				} else {
					if(node == jsr) {
						continue;
					}
					lst = node.getSuccExceptions(); 
				}
				
			
				for(int i=lst.size()-1;i>=0;i--) {
					
					BasicBlock child = (BasicBlock)lst.get(i);
					Integer childid = child.id;
					
					if(mapNewNodes.containsKey(childid)) {
						node.replaceSuccessor(child, (BasicBlock)mapNewNodes.get(childid));
					} else if(common_blocks.contains(child)) {

						// make a copy of the current block
						BasicBlock copy = (BasicBlock)child.clone();
						copy.id = ++last_id;
						// copy all successors
						if(copy.getLastInstruction().opcode == CodeConstants.opc_ret &&
								child.getSuccs().contains(ret)) {
							copy.addSuccessor(ret);	
							child.removeSuccessor(ret);
						} else {
							for(int k=0;k<child.getSuccs().size();k++) {
								copy.addSuccessor((BasicBlock)child.getSuccs().get(k));
							}
						}
						for(int k=0;k<child.getSuccExceptions().size();k++) {
							copy.addSuccessorException((BasicBlock)child.getSuccExceptions().get(k));
						}

						lstNodes.add(copy);
						mapNewNodes.put(childid, copy);
						
						if(last.getPreds().contains(child)) {
							last.addPredecessor(copy);
						}
						
						node.replaceSuccessor(child, copy);
						blocks.addWithKey(copy, copy.id);
					} else {
						// stop at the first fixed node
						//lstNodes.add(child);    
						mapNewNodes.put(childid, child);
					}
				}
				
			}
		}
		
		// note: subroutines won't be copied!
		splitJsrExceptionRanges(common_blocks, mapNewNodes);
	}
	
	private void splitJsrExceptionRanges(HashSet<BasicBlock> common_blocks, HashMap<Integer, BasicBlock> mapNewNodes) {
		
		for(int i=exceptions.size()-1;i>=0;i--) {
			
			ExceptionRangeCFG range = (ExceptionRangeCFG)exceptions.get(i);
			List<BasicBlock> lstRange = range.getProtectedRange();
			
			HashSet<BasicBlock> setBoth = new HashSet<BasicBlock>(common_blocks);
			setBoth.retainAll(lstRange);
			
			if(setBoth.size()>0) {
				List<BasicBlock> lstNewRange; 
			
				if(setBoth.size()==lstRange.size()) {
					lstNewRange = new ArrayList<BasicBlock>(); 
					ExceptionRangeCFG newRange = new ExceptionRangeCFG(lstNewRange,
							(BasicBlock)mapNewNodes.get(range.getHandler().id),range.getExceptionTypes()); 
					exceptions.add(newRange); 
				} else {
					lstNewRange = lstRange; 
				}
				
				for(BasicBlock block : setBoth) {
					lstNewRange.add(mapNewNodes.get(block.id));
				}
			}
		}
		
	}
	
	private void removeJsr(StructMethod mt) {
		removeJsrInstructions(mt.getClassStruct().getPool(), first, DataPoint.getInitialDataPoint(mt));
	}
	
	private void removeJsrInstructions(ConstantPool pool, BasicBlock block, DataPoint data) {
		
		ListStack<VarType> stack = data.getStack();
		
		InstructionSequence seq = block.getSeq();
		for(int i=0;i<seq.length();i++) {
			Instruction instr = seq.getInstr(i);
			
			VarType var = null;
			if(instr.opcode == CodeConstants.opc_astore || instr.opcode == CodeConstants.opc_pop) {
				var = stack.getByOffset(-1);
			}
			
			InstructionImpact.stepTypes(data, instr, pool); 

			switch(instr.opcode) {
			case CodeConstants.opc_jsr:
			case CodeConstants.opc_ret:
				seq.removeInstruction(i);
				i--;
				break;
			case CodeConstants.opc_astore:
			case CodeConstants.opc_pop:
				if(var.type == CodeConstants.TYPE_ADDRESS) {
					seq.removeInstruction(i);
					i--;
				}
			}
			
		}
		
		block.mark = 1;
		
		for(int i=0;i<block.getSuccs().size();i++) {
			BasicBlock suc = (BasicBlock)block.getSuccs().get(i);
			if(suc.mark != 1) {
				removeJsrInstructions(pool, suc, data.copy());
			}
		}
		
		for(int i=0;i<block.getSuccExceptions().size();i++) {
			BasicBlock suc = (BasicBlock)block.getSuccExceptions().get(i);
			if(suc.mark != 1) {
				
				DataPoint point = new DataPoint();
				point.setLocalVariables(new ArrayList<VarType>(data.getLocalVariables()));
				point.getStack().push(new VarType(CodeConstants.TYPE_OBJECT, 0, null)); 
				
				removeJsrInstructions(pool, suc, point);
			}
		}
		
	}
	
	private void setFirstAndLastBlocks() {
		
		first = blocks.get(0);
		
		last = new BasicBlock();
		last.id = ++last_id;
		last.setSeq(new SimpleInstructionSequence());
		
		for(BasicBlock block: blocks) {
			if(block.getSuccs().isEmpty()) {
				last.addPredecessor(block);
			}
		}
	}
	
	public List<BasicBlock> getReversePostOrder() {
		
		LinkedList<BasicBlock> res = new LinkedList<BasicBlock>();
		addToReversePostOrderListIterative(first, res);

		return res;
	}
	
	private void addToReversePostOrderListIterative(BasicBlock root, List<BasicBlock> lst) {
		
		LinkedList<BasicBlock> stackNode = new LinkedList<BasicBlock>();
		LinkedList<Integer> stackIndex = new LinkedList<Integer>();
		
		HashSet<BasicBlock> setVisited = new HashSet<BasicBlock>();
		
		stackNode.add(root);
		stackIndex.add(0);
		
		while(!stackNode.isEmpty()) {
			
			BasicBlock node = stackNode.getLast();
			int index = stackIndex.removeLast();

			setVisited.add(node);
			
			List<BasicBlock> lstSuccs = new ArrayList<BasicBlock>(node.getSuccs());
			lstSuccs.addAll(node.getSuccExceptions());
			
			for(;index<lstSuccs.size();index++) {
				BasicBlock succ = lstSuccs.get(index);
				
				if(!setVisited.contains(succ)) {
					stackIndex.add(index+1);
					
					stackNode.add(succ);
					stackIndex.add(0);
					
					break;
				}
			}
			
			if(index == lstSuccs.size()) {
				lst.add(0, node);
				
				stackNode.removeLast();
			}
		}
		
	}
	
	
	// *****************************************************************************
	// getter and setter methods
	// *****************************************************************************
	
	public VBStyleCollection<BasicBlock, Integer> getBlocks() {
		return blocks;
	}

	public void setBlocks(VBStyleCollection<BasicBlock, Integer> blocks) {
		this.blocks = blocks;
	}

	public BasicBlock getFirst() {
		return first;
	}

	public void setFirst(BasicBlock first) {
		this.first = first;
	}

	public List<BasicBlock> getEndBlocks() {
		return last.getPreds();
	}

	public List<ExceptionRangeCFG> getExceptions() {
		return exceptions;
	}

	public void setExceptions(List<ExceptionRangeCFG> exceptions) {
		this.exceptions = exceptions;
	}


	public BasicBlock getLast() {
		return last;
	}


	public void setLast(BasicBlock last) {
		this.last = last;
	}


	public HashMap<BasicBlock, BasicBlock> getSubroutines() {
		return subroutines;
	}


	public void setSubroutines(HashMap<BasicBlock, BasicBlock> subroutines) {
		this.subroutines = subroutines;
	}


	public HashSet<BasicBlock> getFinallyExits() {
		return finallyExits;
	}


	public void setFinallyExits(HashSet<BasicBlock> finallyExits) {
		this.finallyExits = finallyExits;
	}

}
