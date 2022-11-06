// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.code.cfg;

import org.jetbrains.java.decompiler.code.*;
import org.jetbrains.java.decompiler.code.interpreter.InstructionImpact;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.modules.code.DeadCodeHelper;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.struct.gen.DataPoint;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.ListStack;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.util.*;
import java.util.Map.Entry;

public class ControlFlowGraph implements CodeConstants {

  public int last_id = 0;

  // *****************************************************************************
  // private fields
  // *****************************************************************************

  private VBStyleCollection<BasicBlock, Integer> blocks;

  private BasicBlock first;

  private BasicBlock last;

  private List<ExceptionRangeCFG> exceptions;

  private Map<BasicBlock, BasicBlock> subroutines;

  private final Set<BasicBlock> finallyExits = new HashSet<>();

  // *****************************************************************************
  // constructors
  // *****************************************************************************

  public ControlFlowGraph(InstructionSequence seq) {
    buildBlocks(seq);
  }


  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public void removeMarkers() {
    for (BasicBlock block : blocks) {
      block.mark = 0;
    }
  }

  public String toString() {
    if (blocks == null) return "Empty";

    String new_line_separator = DecompilerContext.getNewLineSeparator();

    StringBuilder buf = new StringBuilder();

    for (BasicBlock block : blocks) {
      buf.append("----- Block ").append(block.id).append(" -----").append(new_line_separator);
      buf.append(block);
      buf.append("----- Edges -----").append(new_line_separator);

      List<BasicBlock> suc = block.getSuccessors();
      for (BasicBlock aSuc : suc) {
        buf.append(">>>>>>>>(regular) Block ").append(aSuc.id).append(new_line_separator);
      }
      suc = block.getSuccessorExceptions();
      for (BasicBlock handler : suc) {
        ExceptionRangeCFG range = getExceptionRange(handler, block);

        if (range == null) {
          buf.append(">>>>>>>>(exception) Block ").append(handler.id).append("\t").append("ERROR: range not found!")
            .append(new_line_separator);
        }
        else {
          List<String> exceptionTypes = range.getExceptionTypes();
          if (exceptionTypes == null) {
            buf.append(">>>>>>>>(exception) Block ").append(handler.id).append("\t").append("NULL").append(new_line_separator);
          }
          else {
            for (String exceptionType : exceptionTypes) {
              buf.append(">>>>>>>>(exception) Block ").append(handler.id).append("\t").append(exceptionType).append(new_line_separator);
            }
          }
        }
      }
      buf.append("----- ----- -----").append(new_line_separator);
    }

    return buf.toString();
  }

  public void inlineJsr(StructClass cl, StructMethod mt) {
    processJsr();
    removeJsr(cl, mt);

    removeMarkers();

    DeadCodeHelper.removeEmptyBlocks(this);
  }

  public void removeBlock(BasicBlock block) {

    while (block.getSuccessors().size() > 0) {
      block.removeSuccessor(block.getSuccessors().get(0));
    }

    while (block.getSuccessorExceptions().size() > 0) {
      block.removeSuccessorException(block.getSuccessorExceptions().get(0));
    }

    while (block.getPredecessors().size() > 0) {
      block.getPredecessors().get(0).removeSuccessor(block);
    }

    while (block.getPredecessorExceptions().size() > 0) {
      block.getPredecessorExceptions().get(0).removeSuccessorException(block);
    }

    last.removePredecessor(block);

    blocks.removeWithKey(block.id);

    for (int i = exceptions.size() - 1; i >= 0; i--) {
      ExceptionRangeCFG range = exceptions.get(i);
      if (range.getHandler() == block) {
        exceptions.remove(i);
      }
      else {
        List<BasicBlock> lstRange = range.getProtectedRange();
        lstRange.remove(block);

        if (lstRange.isEmpty()) {
          exceptions.remove(i);
        }
      }
    }

    subroutines.entrySet().removeIf(ent -> ent.getKey() == block || ent.getValue() == block);
  }

  public ExceptionRangeCFG getExceptionRange(BasicBlock handler, BasicBlock block) {

    //List<ExceptionRangeCFG> ranges = new ArrayList<ExceptionRangeCFG>();

    for (int i = exceptions.size() - 1; i >= 0; i--) {
      ExceptionRangeCFG range = exceptions.get(i);
      if (range.getHandler() == handler && range.getProtectedRange().contains(block)) {
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

    Map<Integer, BasicBlock> mapInstrBlocks = new HashMap<>();
    VBStyleCollection<BasicBlock, Integer> colBlocks = createBasicBlocks(states, instrseq, mapInstrBlocks);

    blocks = colBlocks;

    connectBlocks(colBlocks, mapInstrBlocks);

    setExceptionEdges(instrseq, mapInstrBlocks);

    setSubroutineEdges();

    setFirstAndLastBlocks();
  }

  private static short[] findStartInstructions(InstructionSequence seq) {

    int len = seq.length();
    short[] inststates = new short[len];

    Set<Integer> excSet = new HashSet<>();

    for (ExceptionHandler handler : seq.getExceptionTable().getHandlers()) {
      excSet.add(handler.from_instr);
      excSet.add(handler.to_instr);
      excSet.add(handler.handler_instr);
    }


    for (int i = 0; i < len; i++) {

      // exception blocks
      if (excSet.contains(i)) {
        inststates[i] = 1;
      }

      Instruction instr = seq.getInstr(i);
      switch (instr.group) {
        case GROUP_JUMP:
          inststates[((JumpInstruction)instr).destination] = 1;
        case GROUP_RETURN:
          if (i + 1 < len) {
            inststates[i + 1] = 1;
          }
          break;
        case GROUP_SWITCH:
          SwitchInstruction swinstr = (SwitchInstruction)instr;
          int[] dests = swinstr.getDestinations();
          for (int j = dests.length - 1; j >= 0; j--) {
            inststates[dests[j]] = 1;
          }
          inststates[swinstr.getDefaultDestination()] = 1;
          if (i + 1 < len) {
            inststates[i + 1] = 1;
          }
      }
    }

    // first instruction
    inststates[0] = 1;

    return inststates;
  }


  private VBStyleCollection<BasicBlock, Integer> createBasicBlocks(short[] startblock,
                                                                   InstructionSequence instrseq,
                                                                   Map<Integer, BasicBlock> mapInstrBlocks) {

    VBStyleCollection<BasicBlock, Integer> col = new VBStyleCollection<>();

    InstructionSequence currseq = null;
    List<Integer> lstOffs = null;

    int len = startblock.length;
    short counter = 0;
    int blockoffset = 0;

    BasicBlock currentBlock = null;
    for (int i = 0; i < len; i++) {

      if (startblock[i] == 1) {
        currentBlock = new BasicBlock(++counter);

        currseq = currentBlock.getSeq();
        lstOffs = currentBlock.getOriginalOffsets();

        col.addWithKey(currentBlock, currentBlock.id);

        blockoffset = instrseq.getOffset(i);
      }

      startblock[i] = counter;
      mapInstrBlocks.put(i, currentBlock);

      currseq.addInstruction(instrseq.getInstr(i), instrseq.getOffset(i) - blockoffset);
      lstOffs.add(instrseq.getOffset(i));
    }

    last_id = counter;

    return col;
  }


  private static void connectBlocks(List<BasicBlock> lstbb, Map<Integer, BasicBlock> mapInstrBlocks) {

    for (int i = 0; i < lstbb.size(); i++) {

      BasicBlock block = lstbb.get(i);
      Instruction instr = block.getLastInstruction();

      boolean fallthrough = instr.canFallThrough();
      BasicBlock bTemp;

      switch (instr.group) {
        case GROUP_JUMP -> {
          int dest = ((JumpInstruction)instr).destination;
          bTemp = mapInstrBlocks.get(dest);
          block.addSuccessor(bTemp);
        }
        case GROUP_SWITCH -> {
          SwitchInstruction sinstr = (SwitchInstruction)instr;
          int[] dests = sinstr.getDestinations();

          bTemp = mapInstrBlocks.get(((SwitchInstruction)instr).getDefaultDestination());
          block.addSuccessor(bTemp);
          for (int dest1 : dests) {
            bTemp = mapInstrBlocks.get(dest1);
            block.addSuccessor(bTemp);
          }
        }
      }

      if (fallthrough && i < lstbb.size() - 1) {
        BasicBlock defaultBlock = lstbb.get(i + 1);
        block.addSuccessor(defaultBlock);
      }
    }
  }

  private void setExceptionEdges(InstructionSequence instrseq, Map<Integer, BasicBlock> instrBlocks) {

    exceptions = new ArrayList<>();

    Map<String, ExceptionRangeCFG> mapRanges = new HashMap<>();

    for (ExceptionHandler handler : instrseq.getExceptionTable().getHandlers()) {

      BasicBlock from = instrBlocks.get(handler.from_instr);
      BasicBlock to = instrBlocks.get(handler.to_instr);
      BasicBlock handle = instrBlocks.get(handler.handler_instr);

      String key = from.id + ":" + to.id + ":" + handle.id;

      if (mapRanges.containsKey(key)) {
        ExceptionRangeCFG range = mapRanges.get(key);
        range.addExceptionType(handler.exceptionClass);
      }
      else {

        List<BasicBlock> protectedRange = new ArrayList<>();
        for (int j = from.id; j < to.id; j++) {
          BasicBlock block = blocks.getWithKey(j);
          protectedRange.add(block);
          block.addSuccessorException(handle);
        }

        ExceptionRangeCFG range = new ExceptionRangeCFG(protectedRange, handle, handler.exceptionClass == null
                                                                                ? null
                                                                                : Collections.singletonList(handler.exceptionClass));
        mapRanges.put(key, range);

        exceptions.add(range);
      }
    }
  }

  private void setSubroutineEdges() {

    final Map<BasicBlock, BasicBlock> subroutines = new LinkedHashMap<>();

    for (BasicBlock block : blocks) {

      if (block.getSeq().getLastInstr().opcode == CodeConstants.opc_jsr) {

        LinkedList<BasicBlock> stack = new LinkedList<>();
        LinkedList<LinkedList<BasicBlock>> stackJsrStacks = new LinkedList<>();

        Set<BasicBlock> setVisited = new HashSet<>();

        stack.add(block);
        stackJsrStacks.add(new LinkedList<>());

        while (!stack.isEmpty()) {

          BasicBlock node = stack.removeFirst();
          LinkedList<BasicBlock> jsrstack = stackJsrStacks.removeFirst();

          setVisited.add(node);

          switch (node.getSeq().getLastInstr().opcode) {
            case CodeConstants.opc_jsr -> jsrstack.add(node);
            case CodeConstants.opc_ret -> {
              BasicBlock enter = jsrstack.getLast();
              BasicBlock exit = blocks.getWithKey(enter.id + 1); // FIXME: find successor in a better way

              if (exit != null) {
                if (!node.isSuccessor(exit)) {
                  node.addSuccessor(exit);
                }
                jsrstack.removeLast();
                subroutines.put(enter, exit);
              }
              else {
                throw new RuntimeException("ERROR: last instruction jsr");
              }
            }
          }

          if (!jsrstack.isEmpty()) {
            for (BasicBlock succ : node.getSuccessors()) {
              if (!setVisited.contains(succ)) {
                stack.add(succ);
                stackJsrStacks.add(new LinkedList<>(jsrstack));
              }
            }
          }
        }
      }
    }

    this.subroutines = subroutines;
  }

  private void processJsr() {
    while (true) {
      if (processJsrRanges() == 0) break;
    }
  }

  private static final class JsrRecord {
    private final BasicBlock jsr;
    private final Set<BasicBlock> range;
    private final BasicBlock ret;

    private JsrRecord(BasicBlock jsr, Set<BasicBlock> range, BasicBlock ret) {
      this.jsr = jsr;
      this.range = range;
      this.ret = ret;
    }
  }

  private int processJsrRanges() {

    List<JsrRecord> lstJsrAll = new ArrayList<>();

    // get all jsr ranges
    for (Entry<BasicBlock, BasicBlock> ent : subroutines.entrySet()) {
      BasicBlock jsr = ent.getKey();
      BasicBlock ret = ent.getValue();

      lstJsrAll.add(new JsrRecord(jsr, getJsrRange(jsr, ret), ret));
    }

    // sort ranges
    // FIXME: better sort order
    List<JsrRecord> lstJsr = new ArrayList<>();
    for (JsrRecord arr : lstJsrAll) {
      int i = 0;
      for (; i < lstJsr.size(); i++) {
        JsrRecord arrJsr = lstJsr.get(i);
        if (arrJsr.range.contains(arr.jsr)) {
          break;
        }
      }
      lstJsr.add(i, arr);
    }

    // find the first intersection
    for (int i = 0; i < lstJsr.size(); i++) {
      JsrRecord arr = lstJsr.get(i);
      Set<BasicBlock> set = arr.range;

      for (int j = i + 1; j < lstJsr.size(); j++) {
        JsrRecord arr1 = lstJsr.get(j);
        Set<BasicBlock> set1 = arr1.range;

        if (!set.contains(arr1.jsr) && !set1.contains(arr.jsr)) { // rang 0 doesn't contain entry 1 and vice versa
          Set<BasicBlock> setc = new HashSet<>(set);
          setc.retainAll(set1);

          if (!setc.isEmpty()) {
            splitJsrRange(arr.jsr, arr.ret, setc);
            return 1;
          }
        }
      }
    }

    return 0;
  }

  private Set<BasicBlock> getJsrRange(BasicBlock jsr, BasicBlock ret) {

    Set<BasicBlock> blocks = new HashSet<>();

    List<BasicBlock> lstNodes = new LinkedList<>();
    lstNodes.add(jsr);

    BasicBlock dom = jsr.getSuccessors().get(0);

    while (!lstNodes.isEmpty()) {

      BasicBlock node = lstNodes.remove(0);

      for (int j = 0; j < 2; j++) {
        List<BasicBlock> lst;
        if (j == 0) {
          if (node.getLastInstruction().opcode == CodeConstants.opc_ret) {
            if (node.getSuccessors().contains(ret)) {
              continue;
            }
          }
          lst = node.getSuccessors();
        }
        else {
          if (node == jsr) {
            continue;
          }
          lst = node.getSuccessorExceptions();
        }

        CHILD:
        for (int i = lst.size() - 1; i >= 0; i--) {

          BasicBlock child = lst.get(i);
          if (!blocks.contains(child)) {

            if (node != jsr) {
              for (int k = 0; k < child.getPredecessors().size(); k++) {
                if (!DeadCodeHelper.isDominator(this, child.getPredecessors().get(k), dom)) {
                  continue CHILD;
                }
              }

              for (int k = 0; k < child.getPredecessorExceptions().size(); k++) {
                if (!DeadCodeHelper.isDominator(this, child.getPredecessorExceptions().get(k), dom)) {
                  continue CHILD;
                }
              }
            }

            // last block is a dummy one
            if (child != last) {
              blocks.add(child);
            }

            lstNodes.add(child);
          }
        }
      }
    }

    return blocks;
  }

  private void splitJsrRange(BasicBlock jsr, BasicBlock ret, Set<BasicBlock> common_blocks) {

    List<BasicBlock> lstNodes = new LinkedList<>();
    Map<Integer, BasicBlock> mapNewNodes = new HashMap<>();

    lstNodes.add(jsr);
    mapNewNodes.put(jsr.id, jsr);

    while (!lstNodes.isEmpty()) {

      BasicBlock node = lstNodes.remove(0);

      for (int j = 0; j < 2; j++) {
        List<BasicBlock> lst;
        if (j == 0) {
          if (node.getLastInstruction().opcode == CodeConstants.opc_ret) {
            if (node.getSuccessors().contains(ret)) {
              continue;
            }
          }
          lst = node.getSuccessors();
        }
        else {
          if (node == jsr) {
            continue;
          }
          lst = node.getSuccessorExceptions();
        }


        for (int i = lst.size() - 1; i >= 0; i--) {

          BasicBlock child = lst.get(i);
          Integer childid = child.id;

          if (mapNewNodes.containsKey(childid)) {
            node.replaceSuccessor(child, mapNewNodes.get(childid));
          }
          else if (common_blocks.contains(child)) {
            // make a copy of the current block
            BasicBlock copy = child.clone(++last_id);
            // copy all successors
            if (copy.getLastInstruction().opcode == CodeConstants.opc_ret && child.getSuccessors().contains(ret)) {
              copy.addSuccessor(ret);
              child.removeSuccessor(ret);
            }
            else {
              for (int k = 0; k < child.getSuccessors().size(); k++) {
                copy.addSuccessor(child.getSuccessors().get(k));
              }
            }
            for (int k = 0; k < child.getSuccessorExceptions().size(); k++) {
              copy.addSuccessorException(child.getSuccessorExceptions().get(k));
            }

            lstNodes.add(copy);
            mapNewNodes.put(childid, copy);

            if (last.getPredecessors().contains(child)) {
              last.addPredecessor(copy);
            }

            node.replaceSuccessor(child, copy);
            blocks.addWithKey(copy, copy.id);
          }
          else {
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

  private void splitJsrExceptionRanges(Set<BasicBlock> common_blocks, Map<Integer, BasicBlock> mapNewNodes) {

    for (int i = exceptions.size() - 1; i >= 0; i--) {

      ExceptionRangeCFG range = exceptions.get(i);
      List<BasicBlock> lstRange = range.getProtectedRange();

      HashSet<BasicBlock> setBoth = new HashSet<>(common_blocks);
      setBoth.retainAll(lstRange);

      if (setBoth.size() > 0) {
        List<BasicBlock> lstNewRange;

        if (setBoth.size() == lstRange.size()) {
          lstNewRange = new ArrayList<>();
          ExceptionRangeCFG newRange = new ExceptionRangeCFG(lstNewRange,
                                                             mapNewNodes.get(range.getHandler().id), range.getExceptionTypes());
          exceptions.add(newRange);
        }
        else {
          lstNewRange = lstRange;
        }

        for (BasicBlock block : setBoth) {
          lstNewRange.add(mapNewNodes.get(block.id));
        }
      }
    }
  }

  private void removeJsr(StructClass cl, StructMethod mt) {
    removeJsrInstructions(cl.getPool(), first, DataPoint.getInitialDataPoint(mt));
  }

  private static void removeJsrInstructions(ConstantPool pool, BasicBlock block, DataPoint data) {
    ListStack<VarType> stack = data.getStack();

    InstructionSequence seq = block.getSeq();
    for (int i = 0; i < seq.length(); i++) {
      Instruction instr = seq.getInstr(i);

      VarType var = null;
      if (instr.opcode == CodeConstants.opc_astore || instr.opcode == CodeConstants.opc_pop) {
        var = stack.getByOffset(-1);
      }

      InstructionImpact.stepTypes(data, instr, pool);

      switch (instr.opcode) {
        case CodeConstants.opc_jsr, CodeConstants.opc_ret -> {
          seq.removeInstruction(i);
          i--;
        }
        case CodeConstants.opc_astore, CodeConstants.opc_pop -> {
          if (var.getType() == CodeConstants.TYPE_ADDRESS) {
            seq.removeInstruction(i);
            i--;
          }
        }
      }
    }

    block.mark = 1;

    for (int i = 0; i < block.getSuccessors().size(); i++) {
      BasicBlock suc = block.getSuccessors().get(i);
      if (suc.mark != 1) {
        removeJsrInstructions(pool, suc, data.copy());
      }
    }

    for (int i = 0; i < block.getSuccessorExceptions().size(); i++) {
      BasicBlock suc = block.getSuccessorExceptions().get(i);
      if (suc.mark != 1) {
        DataPoint point = data.copy();
        point.getStack().clear();
        point.getStack().push(new VarType(CodeConstants.TYPE_OBJECT, 0, null));
        removeJsrInstructions(pool, suc, point);
      }
    }
  }

  private void setFirstAndLastBlocks() {

    first = blocks.get(0);

    last = new BasicBlock(++last_id);

    for (BasicBlock block : blocks) {
      if (block.getSuccessors().isEmpty()) {
        last.addPredecessor(block);
      }
    }
  }

  public List<BasicBlock> getReversePostOrder() {

    List<BasicBlock> res = new LinkedList<>();
    addToReversePostOrderListIterative(first, res);

    return res;
  }

  private static void addToReversePostOrderListIterative(BasicBlock root, List<? super BasicBlock> lst) {

    LinkedList<BasicBlock> stackNode = new LinkedList<>();
    LinkedList<Integer> stackIndex = new LinkedList<>();

    Set<BasicBlock> setVisited = new HashSet<>();

    stackNode.add(root);
    stackIndex.add(0);

    while (!stackNode.isEmpty()) {

      BasicBlock node = stackNode.getLast();
      int index = stackIndex.removeLast();

      setVisited.add(node);

      List<BasicBlock> lstSuccs = new ArrayList<>(node.getSuccessors());
      lstSuccs.addAll(node.getSuccessorExceptions());

      for (; index < lstSuccs.size(); index++) {
        BasicBlock succ = lstSuccs.get(index);

        if (!setVisited.contains(succ)) {
          stackIndex.add(index + 1);

          stackNode.add(succ);
          stackIndex.add(0);

          break;
        }
      }

      if (index == lstSuccs.size()) {
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

  public BasicBlock getFirst() {
    return first;
  }

  public void setFirst(BasicBlock first) {
    this.first = first;
  }

  public List<ExceptionRangeCFG> getExceptions() {
    return exceptions;
  }

  public BasicBlock getLast() {
    return last;
  }

  public Set<BasicBlock> getFinallyExits() {
    return finallyExits;
  }
}
