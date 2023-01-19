// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.modules.code;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.Instruction;
import org.jetbrains.java.decompiler.code.InstructionSequence;
import org.jetbrains.java.decompiler.code.SimpleInstructionSequence;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.code.cfg.ControlFlowGraph;
import org.jetbrains.java.decompiler.code.cfg.ExceptionRangeCFG;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.decompiler.FinallyProcessor;

import java.util.*;

public final class DeadCodeHelper {
  public static void removeDeadBlocks(ControlFlowGraph graph) {
    LinkedList<BasicBlock> stack = new LinkedList<>();
    Set<BasicBlock> setStacked = new HashSet<>();

    stack.add(graph.getFirst());
    setStacked.add(graph.getFirst());

    while (!stack.isEmpty()) {
      BasicBlock block = stack.removeFirst();

      List<BasicBlock> successors = new ArrayList<>(block.getSuccessors());
      successors.addAll(block.getSuccessorExceptions());

      for (BasicBlock successor : successors) {
        if (!setStacked.contains(successor)) {
          stack.add(successor);
          setStacked.add(successor);
        }
      }
    }

    Set<BasicBlock> setAllBlocks = new HashSet<>(graph.getBlocks());
    setAllBlocks.removeAll(setStacked);

    for (BasicBlock block : setAllBlocks) {
      graph.removeBlock(block);
    }
  }

  public static void removeEmptyBlocks(ControlFlowGraph graph) {
    List<BasicBlock> blocks = graph.getBlocks();

    boolean cont;
    do {
      cont = false;

      for (int i = blocks.size() - 1; i >= 0; i--) {
        BasicBlock block = blocks.get(i);

        if (removeEmptyBlock(graph, block, false)) {
          cont = true;
          break;
        }
      }
    }
    while (cont);
  }

  private static boolean removeEmptyBlock(ControlFlowGraph graph, BasicBlock block, boolean merging) {
    boolean deletedRanges = false;

    if (block.getSeq().isEmpty()) {
      if (block.getSuccessors().size() > 1) {
        if (block.getPredecessors().size() > 1) {
          // ambiguous block
          throw new RuntimeException("ERROR: empty block with multiple predecessors and successors found");
        }
        else if (!merging) {
          throw new RuntimeException("ERROR: empty block with multiple successors found");
        }
      }

      Set<BasicBlock> setExits = new HashSet<>(graph.getLast().getPredecessors());

      if (block.getPredecessorExceptions().isEmpty() &&
          (!setExits.contains(block) || block.getPredecessors().size() == 1)) {

        if (setExits.contains(block)) {
          BasicBlock predecessor = block.getPredecessors().get(0);

          // FIXME: flag in the basic block
          if (predecessor.getSuccessors().size() != 1 ||
              (!predecessor.getSeq().isEmpty() &&
               predecessor.getSeq().getLastInstr().group == CodeConstants.GROUP_SWITCH)) {
            return false;
          }
        }

        Set<BasicBlock> predecessors = new HashSet<>(block.getPredecessors());
        Set<BasicBlock> successors = new HashSet<>(block.getSuccessors());

        // collecting common exception ranges of predecessors and successors
        Set<BasicBlock> setCommonExceptionHandlers = null;
        for (int i = 0; i < 2; ++i) {
          for (BasicBlock adjacent : i == 0 ? predecessors : successors) {
            if (setCommonExceptionHandlers == null) {
              setCommonExceptionHandlers = new HashSet<>(adjacent.getSuccessorExceptions());
            }
            else {
              setCommonExceptionHandlers.retainAll(adjacent.getSuccessorExceptions());
            }
          }
        }

        // check the block to be in each of the common ranges
        if (setCommonExceptionHandlers != null && !setCommonExceptionHandlers.isEmpty()) {
          for (BasicBlock handler : setCommonExceptionHandlers) {
            if (!block.getSuccessorExceptions().contains(handler)) {
              return false;
            }
          }
        }

        // remove ranges consisting of this one block
        List<ExceptionRangeCFG> lstRanges = graph.getExceptions();
        for (int i = lstRanges.size() - 1; i >= 0; i--) {
          ExceptionRangeCFG range = lstRanges.get(i);
          List<BasicBlock> lst = range.getProtectedRange();

          if (lst.size() == 1 && lst.get(0) == block) {
            if (DecompilerContext.getOption(IFernflowerPreferences.REMOVE_EMPTY_RANGES)) {
              block.removeSuccessorException(range.getHandler());
              lstRanges.remove(i);

              deletedRanges = true;
            }
            else {
              return false;
            }
          }
        }

        // connect remaining nodes
        if (merging) {
          BasicBlock predecessor = block.getPredecessors().get(0);
          predecessor.removeSuccessor(block);

          List<BasicBlock> successorList = new ArrayList<>(block.getSuccessors());
          for (BasicBlock successor : successorList) {
            block.removeSuccessor(successor);
            predecessor.addSuccessor(successor);
          }
        }
        else {
          for (BasicBlock predecessor : predecessors) {
            for (BasicBlock successor : successors) {
              predecessor.replaceSuccessor(block, successor);
            }
          }
        }

        // finally exit edges
        Set<BasicBlock> setFinallyExits = graph.getFinallyExits();
        if (setFinallyExits.contains(block)) {
          setFinallyExits.remove(block);
          setFinallyExits.add(predecessors.iterator().next());
        }

        // replace first if necessary
        if (graph.getFirst() == block) {
          if (successors.size() != 1) {
            throw new RuntimeException("multiple or no entry blocks!");
          }
          else {
            graph.setFirst(successors.iterator().next());
          }
        }

        // remove this block
        graph.removeBlock(block);

        if (deletedRanges) {
          removeDeadBlocks(graph);
        }
      }
    }

    return deletedRanges;
  }

  public static boolean isDominator(ControlFlowGraph graph, BasicBlock block, BasicBlock dom) {
    if (block == dom) {
      return true;
    }

    Set<BasicBlock> marked = new HashSet<>();
    List<BasicBlock> lstNodes = new LinkedList<>();
    lstNodes.add(block);

    while (!lstNodes.isEmpty()) {
      BasicBlock node = lstNodes.remove(0);
      if (marked.contains(node)) {
        continue;
      }
      else {
        marked.add(node);
      }

      if (node == graph.getFirst()) {
        return false;
      }

      for (int i = 0; i < node.getPredecessors().size(); i++) {
        BasicBlock predecessor = node.getPredecessors().get(i);
        if (predecessor != dom && !marked.contains(predecessor)) {
          lstNodes.add(predecessor);
        }
      }

      for (int i = 0; i < node.getPredecessorExceptions().size(); i++) {
        BasicBlock predecessor = node.getPredecessorExceptions().get(i);
        if (predecessor != dom && !marked.contains(predecessor)) {
          lstNodes.add(predecessor);
        }
      }
    }

    return true;
  }

  public static void removeGoTos(ControlFlowGraph graph) {
    for (BasicBlock block : graph.getBlocks()) {
      Instruction instr = block.getLastInstruction();

      if (instr != null && instr.opcode == CodeConstants.opc_goto) {
        block.getSeq().removeLast();
      }
    }

    removeEmptyBlocks(graph);
  }

  public static void connectDummyExitBlock(ControlFlowGraph graph) {
    BasicBlock exit = graph.getLast();
    for (BasicBlock block : new HashSet<>(exit.getPredecessors())) {
      exit.removePredecessor(block);
      block.addSuccessor(exit);
    }
  }

  public static void extendSynchronizedRangeToMonitorExit(ControlFlowGraph graph) {
    while(true) {
      boolean rangeExtended = false;

      for (ExceptionRangeCFG range : graph.getExceptions()) {
        Set<BasicBlock> predecessors = new HashSet<>();
        for (BasicBlock block : range.getProtectedRange()) {
          predecessors.addAll(block.getPredecessors());
        }
        for (BasicBlock basicBlock : range.getProtectedRange()) {
          predecessors.remove(basicBlock);
        }

        if (predecessors.size() != 1) {
          continue; // multiple predecessors -> obfuscated range
        }

        BasicBlock predecessor = predecessors.iterator().next();
        InstructionSequence predecessorSeq = predecessor.getSeq();
        if (predecessorSeq.isEmpty() || predecessorSeq.getLastInstr().opcode != CodeConstants.opc_monitorenter) {
          continue; // not a synchronized range
        }

        boolean monitorExitInRange = false;
        Set<BasicBlock> setProtectedBlocks = new HashSet<>(range.getProtectedRange());
        setProtectedBlocks.add(range.getHandler());

        for (BasicBlock block : setProtectedBlocks) {
          InstructionSequence blockSeq = block.getSeq();
          for (int i = 0; i < blockSeq.length(); i++) {
            if (blockSeq.getInstr(i).opcode == CodeConstants.opc_monitorexit) {
              monitorExitInRange = true;
              break;
            }
          }
          if (monitorExitInRange) {
            break;
          }
        }

        if (monitorExitInRange) {
          continue; // the protected range already contains MonitorExit
        }

        Set<BasicBlock> successors = new HashSet<>();
        for (BasicBlock block : range.getProtectedRange()) {
          successors.addAll(block.getSuccessors());
        }
        for (BasicBlock basicBlock : range.getProtectedRange()) {
          successors.remove(basicBlock);
        }

        if (successors.size() != 1) {
          continue; // non-unique successor
        }

        BasicBlock successor = successors.iterator().next();
        InstructionSequence successorSeq = successor.getSeq();

        int successorMonitorExitIndex = findMonitorExitIndex(successorSeq);
        if (successorMonitorExitIndex < 0) {
          continue; // no MonitorExit found in the single successor block
        }

        BasicBlock handlerBlock = range.getHandler();
        if (handlerBlock.getSuccessors().size() != 1) {
          continue; // non-unique handler successor
        }
        BasicBlock successorHandler = handlerBlock.getSuccessors().get(0);
        InstructionSequence successorHandlerSeq = successorHandler.getSeq();
        if (successorHandlerSeq.isEmpty() || successorHandlerSeq.getLastInstr().opcode != CodeConstants.opc_athrow) {
          continue; // not a standard synchronized range
        }

        int handlerMonitorExitIndex = findMonitorExitIndex(successorHandlerSeq);
        if (handlerMonitorExitIndex < 0) {
          continue; // no MonitorExit found in the handler successor block
        }

        // checks successful, prerequisites satisfied, now extend the range
        if (successorMonitorExitIndex < successorSeq.length() - 1) { // split block
          SimpleInstructionSequence seq = new SimpleInstructionSequence();
          for(int counter = 0; counter < successorMonitorExitIndex; counter++) {
            seq.addInstruction(successorSeq.getInstr(0), -1);
            successorSeq.removeInstruction(0);
          }

          // build a separate block
          BasicBlock newBlock = new BasicBlock(++graph.last_id, seq);

          // insert new block
          for (BasicBlock block : successor.getPredecessors()) {
            block.replaceSuccessor(successor, newBlock);
          }

          newBlock.addSuccessor(successor);
          graph.getBlocks().addWithKey(newBlock, newBlock.id);

          successor = newBlock;
        }

        // copy exception edges and extend protected ranges (successor block)
        BasicBlock rangeExitBlock = successor.getPredecessors().get(0);
        FinallyProcessor.copyExceptionEdges(graph, rangeExitBlock, successor);

        // copy instructions (handler successor block)
        InstructionSequence handlerSeq = handlerBlock.getSeq();
        for(int counter = 0; counter < handlerMonitorExitIndex; counter++) {
          handlerSeq.addInstruction(successorHandlerSeq.getInstr(0), -1);
          successorHandlerSeq.removeInstruction(0);
        }

        rangeExtended = true;
        break;
      }

      if (!rangeExtended) {
        break;
      }
    }
  }

  private static int findMonitorExitIndex(InstructionSequence sequence) {
    int index = -1;
    for (int i = 0; i < sequence.length(); i++) {
      if (sequence.getInstr(i).opcode == CodeConstants.opc_monitorexit) {
        index = i;
        break;
      }
    }
    return index;
  }

  public static void incorporateValueReturns(ControlFlowGraph graph) {
    for (BasicBlock block : graph.getBlocks()) {
      InstructionSequence seq = block.getSeq();

      int len = seq.length();
      if (len > 0 && len < 3) {
        boolean ok = false;

        if (seq.getLastInstr().opcode >= CodeConstants.opc_ireturn && seq.getLastInstr().opcode <= CodeConstants.opc_return) {
          if (len == 1) {
            ok = true;
          }
          else if (seq.getLastInstr().opcode != CodeConstants.opc_return) {
            ok = switch (seq.getInstr(0).opcode) {
              case CodeConstants.opc_iload, CodeConstants.opc_lload, CodeConstants.opc_fload, CodeConstants.opc_dload,
                CodeConstants.opc_aload, CodeConstants.opc_aconst_null, CodeConstants.opc_bipush, CodeConstants.opc_sipush,
                CodeConstants.opc_lconst_0, CodeConstants.opc_lconst_1, CodeConstants.opc_fconst_0, CodeConstants.opc_fconst_1,
                CodeConstants.opc_fconst_2, CodeConstants.opc_dconst_0, CodeConstants.opc_dconst_1, CodeConstants.opc_ldc,
                CodeConstants.opc_ldc_w, CodeConstants.opc_ldc2_w -> true;
              default -> false;
            };
          }
        }

        if (ok) {
          if (!block.getPredecessors().isEmpty()) {
            Set<BasicBlock> predecessorHandlersUnion = new HashSet<>();
            Set<BasicBlock> predecessorHandlersIntersection = new HashSet<>();

            boolean firstPredecessor = true;
            for (BasicBlock predecessor : block.getPredecessors()) {
              if (firstPredecessor) {
                predecessorHandlersIntersection.addAll(predecessor.getSuccessorExceptions());
                firstPredecessor = false;
              }
              else {
                predecessorHandlersIntersection.retainAll(predecessor.getSuccessorExceptions());
              }
              predecessorHandlersUnion.addAll(predecessor.getSuccessorExceptions());
            }

            // add exception ranges from predecessors
            for (BasicBlock basicBlock : block.getSuccessorExceptions()) {
              predecessorHandlersIntersection.remove(basicBlock);
            }

            BasicBlock predecessor = block.getPredecessors().get(0);
            for (BasicBlock handler : predecessorHandlersIntersection) {
              ExceptionRangeCFG range = graph.getExceptionRange(handler, predecessor);
              range.getProtectedRange().add(block);
              block.addSuccessorException(handler);
            }

            // remove redundant ranges
            Set<BasicBlock> setRangesToBeRemoved = new HashSet<>(block.getSuccessorExceptions());
            setRangesToBeRemoved.removeAll(predecessorHandlersUnion);

            for (BasicBlock handler : setRangesToBeRemoved) {
              ExceptionRangeCFG range = graph.getExceptionRange(handler, block);

              if (range.getProtectedRange().size() > 1) {
                range.getProtectedRange().remove(block);
                block.removeSuccessorException(handler);
              }
            }
          }

          if (block.getPredecessors().size() == 1 && block.getPredecessorExceptions().isEmpty()) {
            BasicBlock predecessor = block.getPredecessors().get(0);
            if (predecessor.getSuccessors().size() == 1) {
              // add exception ranges of the predecessor
              for (BasicBlock successor : predecessor.getSuccessorExceptions()) {
                if (!block.getSuccessorExceptions().contains(successor)) {
                  ExceptionRangeCFG range = graph.getExceptionRange(successor, predecessor);
                  range.getProtectedRange().add(block);
                  block.addSuccessorException(successor);
                }
              }

              // remove superfluous ranges from successors
              for (BasicBlock successor : new HashSet<>(block.getSuccessorExceptions())) {
                if (!predecessor.getSuccessorExceptions().contains(successor)) {
                  ExceptionRangeCFG range = graph.getExceptionRange(successor, block);
                  if (range.getProtectedRange().size() > 1) {
                    range.getProtectedRange().remove(block);
                    block.removeSuccessorException(successor);
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  public static void mergeBasicBlocks(ControlFlowGraph graph) {
    while (true) {
      boolean merged = false;
      int originBlocksCount = graph.getBlocks().size();

      for (BasicBlock block : graph.getBlocks()) {
        InstructionSequence seq = block.getSeq();

        if (block.getSuccessors().size() == 1) {
          BasicBlock next = block.getSuccessors().get(0);

          if (next != graph.getLast() && (seq.isEmpty() || seq.getLastInstr().group != CodeConstants.GROUP_SWITCH)) {
            if (next.getPredecessors().size() == 1 && next.getPredecessorExceptions().isEmpty() && next != graph.getFirst()) {
              // TODO: implement a dummy start block
              boolean sameRanges = true;
              for (ExceptionRangeCFG range : graph.getExceptions()) {
                if (range.getProtectedRange().contains(block) ^
                    range.getProtectedRange().contains(next)) {
                  sameRanges = false;
                  break;
                }
              }

              if (sameRanges) {
                seq.addSequence(next.getSeq());
                block.getOriginalOffsets().addAll(next.getOriginalOffsets());
                next.getSeq().clear();

                removeEmptyBlock(graph, next, true);

                merged = true;
                break;
              }
            }
          }
        }
      }

      if (!merged || graph.getBlocks().size() == originBlocksCount) {
        break;
      }
    }
  }
}
