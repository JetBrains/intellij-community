// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.Instruction;
import org.jetbrains.java.decompiler.code.InstructionSequence;
import org.jetbrains.java.decompiler.code.SimpleInstructionSequence;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.code.cfg.ControlFlowGraph;
import org.jetbrains.java.decompiler.code.cfg.ExceptionRangeCFG;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.modules.code.DeadCodeHelper;
import org.jetbrains.java.decompiler.modules.decompiler.StatEdge.EdgeType;
import org.jetbrains.java.decompiler.modules.decompiler.exps.AssignmentExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ExitExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.VarExprent;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectGraph;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.DirectNode;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.FlattenStatementsHelper;
import org.jetbrains.java.decompiler.modules.decompiler.sforms.SSAConstructorSparseEx;
import org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.CatchAllStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement.StatementType;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarProcessor;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.gen.MethodDescriptor;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.*;
import java.util.Map.Entry;

public class FinallyProcessor {
  private final Map<Integer, Integer> finallyBlockIDs = new HashMap<>();
  private final Map<Integer, Integer> catchallBlockIDs = new HashMap<>();

  private final MethodDescriptor methodDescriptor;
  private final VarProcessor varProcessor;

  public FinallyProcessor(MethodDescriptor md, VarProcessor varProc) {
    methodDescriptor = md;
    varProcessor = varProc;
  }

  public boolean iterateGraph(StructClass cl, StructMethod mt, RootStatement root, ControlFlowGraph graph) {
    int bytecodeVersion = mt.getBytecodeVersion();

    LinkedList<Statement> stack = new LinkedList<>();
    stack.add(root);

    while (!stack.isEmpty()) {
      Statement stat = stack.removeLast();

      Statement parent = stat.getParent();
      if (parent != null && parent.type == StatementType.CATCH_ALL &&
          stat == parent.getFirst() && !parent.isCopied()) {

        CatchAllStatement fin = (CatchAllStatement)parent;
        BasicBlock head = fin.getBasichead().getBlock();
        BasicBlock handler = fin.getHandler().getBasichead().getBlock();

        if (catchallBlockIDs.containsKey(handler.id)) {
          // do nothing
        }
        else if (finallyBlockIDs.containsKey(handler.id)) {
          fin.setFinally(true);

          Integer var = finallyBlockIDs.get(handler.id);
          fin.setMonitor(var == null ? null : new VarExprent(var, VarType.VARTYPE_INT, varProcessor));
        }
        else {
          Record inf = getFinallyInformation(cl, mt, root, fin);

          if (inf == null) { // inconsistent finally
            catchallBlockIDs.put(handler.id, null);
          }
          else {
            if (DecompilerContext.getOption(IFernflowerPreferences.FINALLY_DEINLINE) && verifyFinallyEx(graph, fin, inf)) {
              finallyBlockIDs.put(handler.id, null);
            }
            else {
              int varIndex = DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.VAR_COUNTER);
              insertSemaphore(graph, getAllBasicBlocks(fin.getFirst()), head, handler, varIndex, inf, bytecodeVersion);

              finallyBlockIDs.put(handler.id, varIndex);
            }

            DeadCodeHelper.removeDeadBlocks(graph); // e.g. multiple return blocks after a nested finally
            DeadCodeHelper.removeEmptyBlocks(graph);
            DeadCodeHelper.mergeBasicBlocks(graph);
          }

          return true;
        }
      }

      stack.addAll(stat.getStats());
    }

    return false;
  }

  private static final class Record {
    private final int firstCode;
    private final Map<BasicBlock, Boolean> mapLast;

    private Record(int firstCode, Map<BasicBlock, Boolean> mapLast) {
      this.firstCode = firstCode;
      this.mapLast = mapLast;
    }
  }

  private Record getFinallyInformation(StructClass cl, StructMethod mt, RootStatement root, CatchAllStatement fstat) {
    Map<BasicBlock, Boolean> mapLast = new HashMap<>();

    BasicBlockStatement firstBlockStatement = fstat.getHandler().getBasichead();
    BasicBlock firstBasicBlock = firstBlockStatement.getBlock();
    Instruction instrFirst = firstBasicBlock.getInstruction(0);

    int firstCode = switch (instrFirst.opcode) {
      case CodeConstants.opc_pop -> 1;
      case CodeConstants.opc_astore -> 2;
      default -> 0;
    };

    ExprProcessor proc = new ExprProcessor(methodDescriptor, varProcessor);
    proc.processStatement(root, cl);

    SSAConstructorSparseEx ssa = new SSAConstructorSparseEx();
    ssa.splitVariables(root, mt);

    List<Exprent> expressions = firstBlockStatement.getExprents();

    VarVersionPair pair = new VarVersionPair((VarExprent)((AssignmentExprent)expressions.get(firstCode == 2 ? 1 : 0)).getLeft());

    FlattenStatementsHelper flattenHelper = new FlattenStatementsHelper();
    DirectGraph dgraph = flattenHelper.buildDirectGraph(root);

    LinkedList<DirectNode> stack = new LinkedList<>();
    stack.add(dgraph.first);

    Set<DirectNode> setVisited = new HashSet<>();

    while (!stack.isEmpty()) {
      DirectNode node = stack.removeFirst();

      if (setVisited.contains(node)) {
        continue;
      }
      setVisited.add(node);

      BasicBlockStatement blockStatement = null;
      if (node.block != null) {
        blockStatement = node.block;
      }
      else if (node.predecessors.size() == 1) {
        blockStatement = node.predecessors.get(0).block;
      }

      boolean isTrueExit = true;

      if (firstCode != 1) {
        isTrueExit = false;

        for (int i = 0; i < node.exprents.size(); i++) {
          Exprent exprent = node.exprents.get(i);

          if (firstCode == 0) {
            List<Exprent> lst = exprent.getAllExprents();
            lst.add(exprent);

            boolean found = false;
            for (Exprent expr : lst) {
              if (expr.type == Exprent.EXPRENT_VAR && new VarVersionPair((VarExprent)expr).equals(pair)) {
                found = true;
                break;
              }
            }

            if (found) {
              found = false;
              if (exprent.type == Exprent.EXPRENT_EXIT) {
                ExitExprent exit = (ExitExprent)exprent;
                if (exit.getExitType() == ExitExprent.EXIT_THROW && exit.getValue().type == Exprent.EXPRENT_VAR) {
                  found = true;
                }
              }

              if (!found) {
                return null;
              }
              else {
                isTrueExit = true;
              }
            }
          }
          else {  // firstCode == 2
            // searching for a load instruction
            if (exprent.type == Exprent.EXPRENT_ASSIGNMENT) {
              AssignmentExprent assignment = (AssignmentExprent)exprent;
              if (assignment.getRight().type == Exprent.EXPRENT_VAR &&
                  new VarVersionPair((VarExprent)assignment.getRight()).equals(pair)) {
                Exprent next = null;
                if (i == node.exprents.size() - 1) {
                  if (node.successors.size() == 1) {
                    DirectNode nd = node.successors.get(0);
                    if (!nd.exprents.isEmpty()) {
                      next = nd.exprents.get(0);
                    }
                  }
                }
                else {
                  next = node.exprents.get(i + 1);
                }

                boolean found = false;
                if (next != null && next.type == Exprent.EXPRENT_EXIT) {
                  ExitExprent exit = (ExitExprent)next;
                  if (exit.getExitType() == ExitExprent.EXIT_THROW && exit.getValue().type == Exprent.EXPRENT_VAR &&
                      assignment.getLeft().equals(exit.getValue())) {
                    found = true;
                  }
                }

                if (!found) {
                  return null;
                }
                else {
                  isTrueExit = true;
                }
              }
            }
          }
        }
      }

      // find finally exits
      if (blockStatement != null && blockStatement.getBlock() != null) {
        Statement handler = fstat.getHandler();
        for (StatEdge edge : blockStatement.getSuccessorEdges(EdgeType.DIRECT_ALL)) {
          if (edge.getType() != EdgeType.REGULAR && handler.containsStatement(blockStatement)
              && !handler.containsStatement(edge.getDestination())) {
            Boolean existingFlag = mapLast.get(blockStatement.getBlock());
            // note: the dummy node is also processed!
            if (existingFlag == null || !existingFlag) {
              mapLast.put(blockStatement.getBlock(), isTrueExit);
              break;
            }
          }
        }
      }

      stack.addAll(node.successors);
    }

    // an empty `finally` block?
    if (fstat.getHandler().type == StatementType.BASIC_BLOCK) {
      boolean isFirstLast = mapLast.containsKey(firstBasicBlock);
      InstructionSequence seq = firstBasicBlock.getSeq();

      boolean isEmpty = switch (firstCode) {
        case 0 -> isFirstLast && seq.length() == 1;
        case 1 -> seq.length() == 1;
        case 2 -> isFirstLast ? seq.length() == 3 : seq.length() == 1;
        default -> false;
      };

      if (isEmpty) {
        firstCode = 3;
      }
    }

    return new Record(firstCode, mapLast);
  }

  private static void insertSemaphore(ControlFlowGraph graph,
                                      Set<BasicBlock> setTry,
                                      BasicBlock head,
                                      BasicBlock handler,
                                      int var,
                                      Record information,
                                      int bytecodeVersion) {
    Set<BasicBlock> setCopy = new HashSet<>(setTry);

    int finallyType = information.firstCode;
    Map<BasicBlock, Boolean> mapLast = information.mapLast;

    // first and last statements
    removeExceptionInstructionsEx(handler, 1, finallyType);
    for (Entry<BasicBlock, Boolean> entry : mapLast.entrySet()) {
      BasicBlock last = entry.getKey();

      if (entry.getValue()) {
        removeExceptionInstructionsEx(last, 2, finallyType);
        graph.getFinallyExits().add(last);
      }
    }

    // disable semaphore at statement exit points
    for (BasicBlock block : setTry) {
      for (BasicBlock dest : block.getSuccessors()) {
        // break out
        if (dest != graph.getLast() && !setCopy.contains(dest)) {
          // disable semaphore
          SimpleInstructionSequence seq = new SimpleInstructionSequence();
          seq.addInstruction(Instruction.create(CodeConstants.opc_bipush, false, CodeConstants.GROUP_GENERAL, bytecodeVersion, new int[]{0}), -1);
          seq.addInstruction(Instruction.create(CodeConstants.opc_istore, false, CodeConstants.GROUP_GENERAL, bytecodeVersion, new int[]{var}), -1);

          // build a separate block
          BasicBlock newBlock = new BasicBlock(++graph.last_id, seq);

          // insert between block and dest
          block.replaceSuccessor(dest, newBlock);
          newBlock.addSuccessor(dest);
          setCopy.add(newBlock);
          graph.getBlocks().addWithKey(newBlock, newBlock.id);

          // exception ranges
          // FIXME: special case synchronized
          copyExceptionEdges(graph, block, newBlock);
        }
      }
    }

    // enable semaphore at the statement entrance
    BasicBlock newHead = createHeadBlock(graph, 1, var, bytecodeVersion);
    insertBlockBefore(graph, head, newHead);

    // initialize semaphore with false
    BasicBlock newHeadInit = createHeadBlock(graph, 0, var, bytecodeVersion);
    insertBlockBefore(graph, newHead, newHeadInit);

    setCopy.add(newHead);
    setCopy.add(newHeadInit);

    for (BasicBlock hd : new HashSet<>(newHeadInit.getSuccessorExceptions())) {
      ExceptionRangeCFG range = graph.getExceptionRange(hd, newHeadInit);

      if (setCopy.containsAll(range.getProtectedRange())) {
        newHeadInit.removeSuccessorException(hd);
        range.getProtectedRange().remove(newHeadInit);
      }
    }
  }

  @NotNull
  private static BasicBlock createHeadBlock(ControlFlowGraph graph, int value, int var, int bytecodeVersion) {
    SimpleInstructionSequence seq = new SimpleInstructionSequence();
    seq.addInstruction(Instruction.create(CodeConstants.opc_bipush, false, CodeConstants.GROUP_GENERAL, bytecodeVersion, new int[]{value}), -1);
    seq.addInstruction(Instruction.create(CodeConstants.opc_istore, false, CodeConstants.GROUP_GENERAL, bytecodeVersion, new int[]{var}), -1);
    return new BasicBlock(++graph.last_id, seq);
  }

  private static void insertBlockBefore(ControlFlowGraph graph, BasicBlock oldBlock, BasicBlock newBlock) {
    List<BasicBlock> blocks = new ArrayList<>();
    blocks.addAll(oldBlock.getPredecessors());
    blocks.addAll(oldBlock.getPredecessorExceptions());

    // replace predecessors
    for (BasicBlock predecessor : blocks) {
      predecessor.replaceSuccessor(oldBlock, newBlock);
    }

    // copy exception edges and extend protected ranges
    for (BasicBlock hd : oldBlock.getSuccessorExceptions()) {
      newBlock.addSuccessorException(hd);

      ExceptionRangeCFG range = graph.getExceptionRange(hd, oldBlock);
      range.getProtectedRange().add(newBlock);
    }

    // replace handler
    for (ExceptionRangeCFG range : graph.getExceptions()) {
      if (range.getHandler() == oldBlock) {
        range.setHandler(newBlock);
      }
    }

    newBlock.addSuccessor(oldBlock);
    graph.getBlocks().addWithKey(newBlock, newBlock.id);
    if (graph.getFirst() == oldBlock) {
      graph.setFirst(newBlock);
    }
  }

  private static Set<BasicBlock> getAllBasicBlocks(Statement stat) {
    List<Statement> lst = new LinkedList<>();
    lst.add(stat);

    int index = 0;
    do {
      Statement st = lst.get(index);
      if (st.type == StatementType.BASIC_BLOCK) {
        index++;
      }
      else {
        lst.addAll(st.getStats());
        lst.remove(index);
      }
    }
    while (index < lst.size());

    Set<BasicBlock> res = new HashSet<>();
    for (Statement st : lst) {
      res.add(((BasicBlockStatement)st).getBlock());
    }
    return res;
  }

  private boolean verifyFinallyEx(ControlFlowGraph graph, CatchAllStatement fstat, Record information) {
    Set<BasicBlock> tryBlocks = getAllBasicBlocks(fstat.getFirst());
    Set<BasicBlock> catchBlocks = getAllBasicBlocks(fstat.getHandler());

    int finallyType = information.firstCode;
    Map<BasicBlock, Boolean> mapLast = information.mapLast;

    BasicBlock first = fstat.getHandler().getBasichead().getBlock();
    boolean skippedFirst = false;

    if (finallyType == 3) {
      // empty finally
      removeExceptionInstructionsEx(first, 3, finallyType);

      if (mapLast.containsKey(first)) {
        graph.getFinallyExits().add(first);
      }

      return true;
    }
    else {
      if (first.getSeq().length() == 1 && finallyType > 0) {
        BasicBlock firstSuccessor = first.getSuccessors().get(0);
        if (catchBlocks.contains(firstSuccessor)) {
          first = firstSuccessor;
          skippedFirst = true;
        }
      }
    }

    // identify start blocks
    Set<BasicBlock> startBlocks = new HashSet<>();
    for (BasicBlock block : tryBlocks) {
      startBlocks.addAll(block.getSuccessors());
    }
    // `throw` in the `try` body will point directly to the dummy exit, so remove it
    startBlocks.remove(graph.getLast());
    startBlocks.removeAll(tryBlocks);

    List<Area> areas = new ArrayList<>();
    for (BasicBlock start : startBlocks) {
      Area arr = compareSubGraphsEx(graph, start, catchBlocks, first, finallyType, mapLast, skippedFirst);
      if (arr == null) {
        return false;
      }
      areas.add(arr);
    }

    // delete areas
    for (Area area : areas) {
      deleteArea(graph, area);
    }

    // INFO: Empty basic blocks may remain in the graph!
    for (Entry<BasicBlock, Boolean> entry : mapLast.entrySet()) {
      BasicBlock last = entry.getKey();

      if (entry.getValue()) {
        removeExceptionInstructionsEx(last, 2, finallyType);
        graph.getFinallyExits().add(last);
      }
    }

    removeExceptionInstructionsEx(fstat.getHandler().getBasichead().getBlock(), 1, finallyType);

    return true;
  }

  private static final class Area {
    private final BasicBlock start;
    private final Set<BasicBlock> sample;
    private final BasicBlock next;

    private Area(BasicBlock start, Set<BasicBlock> sample, BasicBlock next) {
      this.start = start;
      this.sample = sample;
      this.next = next;
    }
  }

  private Area compareSubGraphsEx(ControlFlowGraph graph,
                                  BasicBlock startSample,
                                  Set<BasicBlock> catchBlocks,
                                  BasicBlock startCatch,
                                  int finallyType,
                                  Map<BasicBlock, Boolean> mapLast,
                                  boolean skippedFirst) {
    class BlockStackEntry {
      public final BasicBlock blockCatch;
      public final BasicBlock blockSample;

      // TODO: correct handling (merging) of multiple paths
      public final List<int[]> lstStoreVars;

      BlockStackEntry(BasicBlock blockCatch, BasicBlock blockSample, List<int[]> lstStoreVars) {
        this.blockCatch = blockCatch;
        this.blockSample = blockSample;
        this.lstStoreVars = new ArrayList<>(lstStoreVars);
      }
    }

    List<BlockStackEntry> stack = new LinkedList<>();

    Set<BasicBlock> setSample = new HashSet<>();

    Map<String, BasicBlock[]> mapNext = new HashMap<>();

    stack.add(new BlockStackEntry(startCatch, startSample, new ArrayList<>()));

    while (!stack.isEmpty()) {
      BlockStackEntry entry = stack.remove(0);
      BasicBlock blockCatch = entry.blockCatch;
      BasicBlock blockSample = entry.blockSample;

      boolean isFirstBlock = !skippedFirst && blockCatch == startCatch;
      boolean isLastBlock = mapLast.containsKey(blockCatch);
      boolean isTrueLastBlock = isLastBlock && mapLast.get(blockCatch);

      int compareType = (isFirstBlock ? 1 : 0) | (isTrueLastBlock ? 2 : 0);
      if (!compareBasicBlocksEx(graph, blockCatch, blockSample, compareType, finallyType, entry.lstStoreVars)) {
        return null;
      }

      if (blockSample.getSuccessors().size() != blockCatch.getSuccessors().size()) {
        return null;
      }

      setSample.add(blockSample);

      // direct successors
      for (int i = 0; i < blockCatch.getSuccessors().size(); i++) {
        BasicBlock sucCatch = blockCatch.getSuccessors().get(i);
        BasicBlock sucSample = blockSample.getSuccessors().get(i);

        if (catchBlocks.contains(sucCatch) && !setSample.contains(sucSample)) {
          stack.add(new BlockStackEntry(sucCatch, sucSample, entry.lstStoreVars));
        }
      }

      // exception successors
      if (isLastBlock && blockSample.getSeq().isEmpty()) {
        // do nothing, blockSample will be removed anyway
      }
      else {
        if (blockCatch.getSuccessorExceptions().size() == blockSample.getSuccessorExceptions().size()) {
          for (int i = 0; i < blockCatch.getSuccessorExceptions().size(); i++) {
            BasicBlock sucCatch = blockCatch.getSuccessorExceptions().get(i);
            BasicBlock sucSample = blockSample.getSuccessorExceptions().get(i);

            String excCatch = graph.getExceptionRange(sucCatch, blockCatch).getUniqueExceptionsString();
            String excSample = graph.getExceptionRange(sucSample, blockSample).getUniqueExceptionsString();

            // FIXME: compare handlers if possible
            if (Objects.equals(excCatch, excSample)) {
              if (catchBlocks.contains(sucCatch) && !setSample.contains(sucSample)) {
                List<int[]> lst = entry.lstStoreVars;

                if (sucCatch.getSeq().length() > 0 && sucSample.getSeq().length() > 0) {
                  Instruction instrCatch = sucCatch.getSeq().getInstr(0);
                  Instruction instrSample = sucSample.getSeq().getInstr(0);

                  if (instrCatch.opcode == CodeConstants.opc_astore &&
                      instrSample.opcode == CodeConstants.opc_astore) {
                    lst = new ArrayList<>(lst);
                    lst.add(new int[]{instrCatch.operand(0), instrSample.operand(0)});
                  }
                }

                stack.add(new BlockStackEntry(sucCatch, sucSample, lst));
              }
            }
            else {
              return null;
            }
          }
        }
        else {
          return null;
        }
      }

      if (isLastBlock) {
        Set<BasicBlock> successors = new HashSet<>(blockSample.getSuccessors());
        successors.removeAll(setSample);

        for (BlockStackEntry stackEntry : stack) {
          successors.remove(stackEntry.blockSample);
        }

        for (BasicBlock successor : successors) {
          if (graph.getLast() != successor) { // FIXME: why?
            mapNext.put(blockSample.id + "#" + successor.id, new BasicBlock[]{blockSample, successor, isTrueLastBlock ? successor : null});
          }
        }
      }
    }

    return new Area(startSample, setSample, getUniqueNext(graph, new HashSet<>(mapNext.values())));
  }

  private static BasicBlock getUniqueNext(ControlFlowGraph graph, Set<BasicBlock[]> setNext) {
    // precondition: there is at most one true exit path in the `finally` statement
    BasicBlock next = null;
    boolean multiple = false;

    for (BasicBlock[] arr : setNext) {
      if (arr[2] != null) {
        next = arr[1];
        multiple = false;
        break;
      }
      else {
        if (next == null) {
          next = arr[1];
        }
        else if (next != arr[1]) {
          multiple = true;
        }

        if (arr[1].getPredecessors().size() == 1) {
          next = arr[1];
        }
      }
    }

    if (multiple) { // TODO: generic solution
      for (BasicBlock[] arr : setNext) {
        BasicBlock block = arr[1];

        if (block != next) {
          if (InterpreterUtil.equalSets(next.getSuccessors(), block.getSuccessors())) {
            InstructionSequence seqNext = next.getSeq();
            InstructionSequence seqBlock = block.getSeq();

            if (seqNext.length() == seqBlock.length()) {
              for (int i = 0; i < seqNext.length(); i++) {
                Instruction instrNext = seqNext.getInstr(i);
                Instruction instrBlock = seqBlock.getInstr(i);

                if (!Instruction.equals(instrNext, instrBlock)) {
                  return null;
                }
                for (int j = 0; j < instrNext.operandsCount(); j++) {
                  if (instrNext.operand(j) != instrBlock.operand(j)) {
                    return null;
                  }
                }
              }
            }
            else {
              return null;
            }
          }
          else {
            return null;
          }
        }
      }
      for (BasicBlock[] arr : setNext) {
        if (arr[1] != next) {
          // FIXME: exception edge possible?
          arr[0].removeSuccessor(arr[1]);
          arr[0].addSuccessor(next);
        }
      }

      DeadCodeHelper.removeDeadBlocks(graph);
    }

    return next;
  }

  private boolean compareBasicBlocksEx(ControlFlowGraph graph,
                                       BasicBlock pattern,
                                       BasicBlock sample,
                                       int type,
                                       int finallyType,
                                       List<int[]> lstStoreVars) {
    InstructionSequence seqPattern = pattern.getSeq();
    InstructionSequence seqSample = sample.getSeq();

    if (type != 0) {
      seqPattern = seqPattern.clone();

      if ((type & 1) > 0) { // first
        if (finallyType > 0) {
          seqPattern.removeInstruction(0);
        }
      }

      if ((type & 2) > 0) { // last
        if (finallyType == 0 || finallyType == 2) {
          seqPattern.removeLast();
        }

        if (finallyType == 2) {
          seqPattern.removeLast();
        }
      }
    }

    if (seqPattern.length() > seqSample.length()) {
      return false;
    }

    for (int i = 0; i < seqPattern.length(); i++) {
      Instruction instrPattern = seqPattern.getInstr(i);
      Instruction instrSample = seqSample.getInstr(i);

      // compare instructions with respect to jumps
      if (!equalInstructions(instrPattern, instrSample, lstStoreVars)) {
        return false;
      }
    }

    if (seqPattern.length() < seqSample.length()) { // split in two blocks
      SimpleInstructionSequence seq = new SimpleInstructionSequence();
      LinkedList<Integer> oldOffsets = new LinkedList<>();
      for (int i = seqSample.length() - 1; i >= seqPattern.length(); i--) {
        seq.addInstruction(0, seqSample.getInstr(i), -1);
        oldOffsets.addFirst(sample.getOriginalOffset(i));
        seqSample.removeInstruction(i);
      }

      BasicBlock newBlock = new BasicBlock(++graph.last_id, seq);
      newBlock.getOriginalOffsets().addAll(oldOffsets);

      List<BasicBlock> lstTemp = new ArrayList<>(sample.getSuccessors());

      // move successors
      for (BasicBlock suc : lstTemp) {
        sample.removeSuccessor(suc);
        newBlock.addSuccessor(suc);
      }

      sample.addSuccessor(newBlock);

      graph.getBlocks().addWithKey(newBlock, newBlock.id);

      Set<BasicBlock> setFinallyExits = graph.getFinallyExits();
      if (setFinallyExits.contains(sample)) {
        setFinallyExits.remove(sample);
        setFinallyExits.add(newBlock);
      }

      copyExceptionEdges(graph, sample, newBlock);
    }

    return true;
  }

  // copy exception edges and extend protected ranges
  public static void copyExceptionEdges(ControlFlowGraph graph, BasicBlock sample, BasicBlock newBlock) {
    for (int i = 0; i < sample.getSuccessorExceptions().size(); i++) {
      BasicBlock hd = sample.getSuccessorExceptions().get(i);
      newBlock.addSuccessorException(hd);
      ExceptionRangeCFG range = graph.getExceptionRange(hd, sample);
      range.getProtectedRange().add(newBlock);
    }
  }

  public boolean equalInstructions(Instruction first, Instruction second, List<int[]> lstStoreVars) {
    if (!Instruction.equals(first, second)) {
      return false;
    }

    if (first.group != CodeConstants.GROUP_JUMP) { // FIXME: switch comparison
      for (int i = 0; i < first.operandsCount(); i++) {
        int firstOp = first.operand(i);
        int secondOp = second.operand(i);
        if (firstOp != secondOp) {
          // a-load/store instructions
          if (first.opcode == CodeConstants.opc_aload || first.opcode == CodeConstants.opc_astore) {
            for (int[] arr : lstStoreVars) {
              if (arr[0] == firstOp && arr[1] == secondOp) {
                return true;
              }
            }
          }

          return false;
        }
      }
    }

    return true;
  }

  private static void deleteArea(ControlFlowGraph graph, Area area) {
    BasicBlock start = area.start;
    BasicBlock next = area.next;

    if (start == next) {
      return;
    }

    if (next == null) {
      // dummy exit block
      next = graph.getLast();
    }

    // collecting common exception ranges of predecessors and successors
    Set<BasicBlock> setCommonExceptionHandlers = new HashSet<>(next.getSuccessorExceptions());
    for (BasicBlock predecessor : start.getPredecessors()) {
      setCommonExceptionHandlers.retainAll(predecessor.getSuccessorExceptions());
    }

    boolean isOutsideRange = false;

    Set<BasicBlock> setPredecessors = new HashSet<>(start.getPredecessors());

    // replace start with next
    for (BasicBlock predecessor : setPredecessors) {
      predecessor.replaceSuccessor(start, next);
    }

    Set<BasicBlock> setBlocks = area.sample;

    Set<ExceptionRangeCFG> setCommonRemovedExceptionRanges = null;

    // remove all the blocks in between
    for (BasicBlock block : setBlocks) {
      // artificial basic blocks (those resulting from splitting) may belong to more than one area
      if (graph.getBlocks().containsKey(block.id)) {
        if (!new HashSet<>(block.getSuccessorExceptions()).containsAll(setCommonExceptionHandlers)) {
          isOutsideRange = true;
        }

        Set<ExceptionRangeCFG> setRemovedExceptionRanges = new HashSet<>();
        for (BasicBlock handler : block.getSuccessorExceptions()) {
          setRemovedExceptionRanges.add(graph.getExceptionRange(handler, block));
        }

        if (setCommonRemovedExceptionRanges == null) {
          setCommonRemovedExceptionRanges = setRemovedExceptionRanges;
        }
        else {
          setCommonRemovedExceptionRanges.retainAll(setRemovedExceptionRanges);
        }

        // shift extern edges on split blocks
        if (block.getSeq().isEmpty() && block.getSuccessors().size() == 1) {
          BasicBlock successor = block.getSuccessors().get(0);
          for (BasicBlock predecessor : new ArrayList<>(block.getPredecessors())) {
            if (!setBlocks.contains(predecessor)) {
              predecessor.replaceSuccessor(block, successor);
            }
          }

          if (graph.getFirst() == block) {
            graph.setFirst(successor);
          }
        }

        graph.removeBlock(block);
      }
    }

    if (isOutsideRange) {
      // new empty block
      BasicBlock emptyBlock = new BasicBlock(++graph.last_id);
      graph.getBlocks().addWithKey(emptyBlock, emptyBlock.id);

      // add to ranges if necessary
      for (ExceptionRangeCFG range : setCommonRemovedExceptionRanges) {
        emptyBlock.addSuccessorException(range.getHandler());
        range.getProtectedRange().add(emptyBlock);
      }

      // insert between predecessors and next
      emptyBlock.addSuccessor(next);
      for (BasicBlock predecessor : setPredecessors) {
        predecessor.replaceSuccessor(next, emptyBlock);
      }
    }
  }

  private static void removeExceptionInstructionsEx(BasicBlock block, int blockType, int finallyType) {
    InstructionSequence seq = block.getSeq();

    if (finallyType == 3) { // empty finally handler
      for (int i = seq.length() - 1; i >= 0; i--) {
        seq.removeInstruction(i);
      }
    }
    else {
      if ((blockType & 1) > 0) { // first
        if (finallyType == 2 || finallyType == 1) { // `AStore` or `Pop`
          seq.removeInstruction(0);
        }
      }

      if ((blockType & 2) > 0) { // last
        if (finallyType == 2 || finallyType == 0) {
          seq.removeLast();
        }

        if (finallyType == 2) { // `AStore`
          seq.removeLast();
        }
      }
    }
  }
}
