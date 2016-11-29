/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.decompiler.modules.decompiler.deobfuscator;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.code.Instruction;
import org.jetbrains.java.decompiler.code.InstructionSequence;
import org.jetbrains.java.decompiler.code.SimpleInstructionSequence;
import org.jetbrains.java.decompiler.code.cfg.BasicBlock;
import org.jetbrains.java.decompiler.code.cfg.ControlFlowGraph;
import org.jetbrains.java.decompiler.code.cfg.ExceptionRangeCFG;
import org.jetbrains.java.decompiler.modules.decompiler.decompose.GenericDominatorEngine;
import org.jetbrains.java.decompiler.modules.decompiler.decompose.IGraph;
import org.jetbrains.java.decompiler.modules.decompiler.decompose.IGraphNode;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.*;
import java.util.Map.Entry;

public class ExceptionDeobfuscator {

  private static class Range {
    private final BasicBlock handler;
    private final String uniqueStr;
    private final Set<BasicBlock> protectedRange;
    private final ExceptionRangeCFG rangeCFG;

    private Range(BasicBlock handler, String uniqueStr, Set<BasicBlock> protectedRange, ExceptionRangeCFG rangeCFG) {
      this.handler = handler;
      this.uniqueStr = uniqueStr;
      this.protectedRange = protectedRange;
      this.rangeCFG = rangeCFG;
    }
  }

  public static void restorePopRanges(ControlFlowGraph graph) {

    List<Range> lstRanges = new ArrayList<>();

    // aggregate ranges
    for (ExceptionRangeCFG range : graph.getExceptions()) {
      boolean found = false;
      for (Range arr : lstRanges) {
        if (arr.handler == range.getHandler() && InterpreterUtil.equalObjects(range.getUniqueExceptionsString(), arr.uniqueStr)) {
          arr.protectedRange.addAll(range.getProtectedRange());
          found = true;
          break;
        }
      }

      if (!found) {
        // doesn't matter, which range chosen
        lstRanges.add(new Range(range.getHandler(), range.getUniqueExceptionsString(), new HashSet<>(range.getProtectedRange()), range));
      }
    }

    // process aggregated ranges
    for (Range range : lstRanges) {

      if (range.uniqueStr != null) {

        BasicBlock handler = range.handler;
        InstructionSequence seq = handler.getSeq();

        Instruction firstinstr;
        if (seq.length() > 0) {
          firstinstr = seq.getInstr(0);

          if (firstinstr.opcode == CodeConstants.opc_pop ||
              firstinstr.opcode == CodeConstants.opc_astore) {
            Set<BasicBlock> setrange = new HashSet<>(range.protectedRange);

            for (Range range_super : lstRanges) { // finally or strict superset

              if (range != range_super) {

                Set<BasicBlock> setrange_super = new HashSet<>(range_super.protectedRange);

                if (!setrange.contains(range_super.handler) && !setrange_super.contains(handler)
                    && (range_super.uniqueStr == null || setrange_super.containsAll(setrange))) {

                  if (range_super.uniqueStr == null) {
                    setrange_super.retainAll(setrange);
                  }
                  else {
                    setrange_super.removeAll(setrange);
                  }

                  if (!setrange_super.isEmpty()) {

                    BasicBlock newblock = handler;

                    // split the handler
                    if (seq.length() > 1) {
                      newblock = new BasicBlock(++graph.last_id);
                      InstructionSequence newseq = new SimpleInstructionSequence();
                      newseq.addInstruction(firstinstr.clone(), -1);

                      newblock.setSeq(newseq);
                      graph.getBlocks().addWithKey(newblock, newblock.id);


                      List<BasicBlock> lstTemp = new ArrayList<>();
                      lstTemp.addAll(handler.getPreds());
                      lstTemp.addAll(handler.getPredExceptions());

                      // replace predecessors
                      for (BasicBlock pred : lstTemp) {
                        pred.replaceSuccessor(handler, newblock);
                      }

                      // replace handler
                      for (ExceptionRangeCFG range_ext : graph.getExceptions()) {
                        if (range_ext.getHandler() == handler) {
                          range_ext.setHandler(newblock);
                        }
                        else if (range_ext.getProtectedRange().contains(handler)) {
                          newblock.addSuccessorException(range_ext.getHandler());
                          range_ext.getProtectedRange().add(newblock);
                        }
                      }

                      newblock.addSuccessor(handler);
                      if (graph.getFirst() == handler) {
                        graph.setFirst(newblock);
                      }

                      // remove the first pop in the handler
                      seq.removeInstruction(0);
                    }

                    newblock.addSuccessorException(range_super.handler);
                    range_super.rangeCFG.getProtectedRange().add(newblock);

                    handler = range.rangeCFG.getHandler();
                    seq = handler.getSeq();
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  public static void insertEmptyExceptionHandlerBlocks(ControlFlowGraph graph) {

    Set<BasicBlock> setVisited = new HashSet<>();

    for (ExceptionRangeCFG range : graph.getExceptions()) {
      BasicBlock handler = range.getHandler();

      if (setVisited.contains(handler)) {
        continue;
      }
      setVisited.add(handler);

      BasicBlock emptyblock = new BasicBlock(++graph.last_id);
      graph.getBlocks().addWithKey(emptyblock, emptyblock.id);

      List<BasicBlock> lstTemp = new ArrayList<>();
      // only exception predecessors considered
      lstTemp.addAll(handler.getPredExceptions());

      // replace predecessors
      for (BasicBlock pred : lstTemp) {
        pred.replaceSuccessor(handler, emptyblock);
      }

      // replace handler
      for (ExceptionRangeCFG range_ext : graph.getExceptions()) {
        if (range_ext.getHandler() == handler) {
          range_ext.setHandler(emptyblock);
        }
        else if (range_ext.getProtectedRange().contains(handler)) {
          emptyblock.addSuccessorException(range_ext.getHandler());
          range_ext.getProtectedRange().add(emptyblock);
        }
      }

      emptyblock.addSuccessor(handler);
      if (graph.getFirst() == handler) {
        graph.setFirst(emptyblock);
      }
    }
  }

  public static void removeEmptyRanges(ControlFlowGraph graph) {

    List<ExceptionRangeCFG> lstRanges = graph.getExceptions();
    for (int i = lstRanges.size() - 1; i >= 0; i--) {
      ExceptionRangeCFG range = lstRanges.get(i);

      boolean isEmpty = true;
      for (BasicBlock block : range.getProtectedRange()) {
        if (!block.getSeq().isEmpty()) {
          isEmpty = false;
          break;
        }
      }

      if (isEmpty) {
        for (BasicBlock block : range.getProtectedRange()) {
          block.removeSuccessorException(range.getHandler());
        }

        lstRanges.remove(i);
      }
    }
  }

  public static void removeCircularRanges(final ControlFlowGraph graph) {

    GenericDominatorEngine engine = new GenericDominatorEngine(new IGraph() {
      public List<? extends IGraphNode> getReversePostOrderList() {
        return graph.getReversePostOrder();
      }

      public Set<? extends IGraphNode> getRoots() {
        return new HashSet<>(Arrays.asList(new IGraphNode[]{graph.getFirst()}));
      }
    });

    engine.initialize();

    List<ExceptionRangeCFG> lstRanges = graph.getExceptions();
    for (int i = lstRanges.size() - 1; i >= 0; i--) {
      ExceptionRangeCFG range = lstRanges.get(i);

      BasicBlock handler = range.getHandler();
      List<BasicBlock> rangeList = range.getProtectedRange();

      if (rangeList.contains(handler)) {  // TODO: better removing strategy

        List<BasicBlock> lstRemBlocks = getReachableBlocksRestricted(range, engine);

        if (lstRemBlocks.size() < rangeList.size() || rangeList.size() == 1) {
          for (BasicBlock block : lstRemBlocks) {
            block.removeSuccessorException(handler);
            rangeList.remove(block);
          }
        }

        if (rangeList.isEmpty()) {
          lstRanges.remove(i);
        }
      }
    }
  }

  private static List<BasicBlock> getReachableBlocksRestricted(ExceptionRangeCFG range, GenericDominatorEngine engine) {

    List<BasicBlock> lstRes = new ArrayList<>();

    LinkedList<BasicBlock> stack = new LinkedList<>();
    Set<BasicBlock> setVisited = new HashSet<>();

    BasicBlock handler = range.getHandler();
    stack.addFirst(handler);

    while (!stack.isEmpty()) {
      BasicBlock block = stack.removeFirst();

      setVisited.add(block);

      if (range.getProtectedRange().contains(block) && engine.isDominator(block, handler)) {
        lstRes.add(block);

        List<BasicBlock> lstSuccs = new ArrayList<>(block.getSuccs());
        lstSuccs.addAll(block.getSuccExceptions());

        for (BasicBlock succ : lstSuccs) {
          if (!setVisited.contains(succ)) {
            stack.add(succ);
          }
        }
      }
    }

    return lstRes;
  }


  public static boolean hasObfuscatedExceptions(ControlFlowGraph graph) {

    Map<BasicBlock, Set<BasicBlock>> mapRanges = new HashMap<>();
    for (ExceptionRangeCFG range : graph.getExceptions()) {
      Set<BasicBlock> set = mapRanges.get(range.getHandler());
      if (set == null) {
        mapRanges.put(range.getHandler(), set = new HashSet<>());
      }
      set.addAll(range.getProtectedRange());
    }

    for (Entry<BasicBlock, Set<BasicBlock>> ent : mapRanges.entrySet()) {
      Set<BasicBlock> setEntries = new HashSet<>();

      for (BasicBlock block : ent.getValue()) {
        Set<BasicBlock> setTemp = new HashSet<>(block.getPreds());
        setTemp.removeAll(ent.getValue());

        if (!setTemp.isEmpty()) {
          setEntries.add(block);
        }
      }

      if (!setEntries.isEmpty()) {
        if (setEntries.size() > 1 /*|| ent.getValue().contains(first)*/) {
          return true;
        }
      }
    }

    return false;
  }
}
