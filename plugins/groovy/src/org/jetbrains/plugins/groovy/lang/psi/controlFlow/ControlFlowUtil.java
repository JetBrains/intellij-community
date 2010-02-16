/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.TIntHashSet;
import gnu.trove.TObjectIntHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class ControlFlowUtil {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.controlFlow.ControlFlowUtil");

  public static int[] postorder(Instruction[] flow) {
    int[] result = new int[flow.length];
    boolean[] visited = new boolean[flow.length];
    for (int i = 0; i < result.length; i++) visited[i] = false;

    int N = flow.length;
    for (int i = 0; i < flow.length; i++) { //graph might not be connected
      if (!visited[i]) N = doVisitForPostorder(flow[i], N, result, visited);
    }

    LOG.assertTrue(N == 0);
    return result;
  }

  private static int doVisitForPostorder(Instruction curr, int currN, int[] postorder, boolean[] visited) {
    visited[curr.num()] = true;
    for (Instruction succ : curr.allSucc()) {
      if (!visited[succ.num()]) {
        currN = doVisitForPostorder(succ, currN, postorder, visited);
      }
    }
    postorder[curr.num()] = --currN;
    return currN;
  }

  public static ReadWriteVariableInstruction[] getReadsWithoutPriorWrites(Instruction[] flow) {
    List<ReadWriteVariableInstruction> result = new ArrayList<ReadWriteVariableInstruction>();
    TObjectIntHashMap<String> namesIndex = buildNamesIndex(flow);

    TIntHashSet[] definitelyAssigned = new TIntHashSet[flow.length];

    int[] postorder = postorder(flow);
    int[] invpostorder = invPostorder(postorder);

    findReadsBeforeWrites(flow, definitelyAssigned, result, namesIndex, postorder, invpostorder);

    return result.toArray(new ReadWriteVariableInstruction[result.size()]);
  }

  private static int[] invPostorder(int[] postorder) {
    int[] result = new int[postorder.length];
    for (int i = 0; i < postorder.length; i++) {
      result[postorder[i]] = i;
    }

    return result;
  }

  private static TObjectIntHashMap<String> buildNamesIndex(Instruction[] flow) {
    TObjectIntHashMap<String> namesIndex = new TObjectIntHashMap<String>();
    int idx = 0;
    for (Instruction instruction : flow) {
      if (instruction instanceof ReadWriteVariableInstruction) {
        String name = ((ReadWriteVariableInstruction) instruction).getVariableName();
        if (!namesIndex.contains(name)) {
          namesIndex.put(name, idx++);
        }
      }
    }
    return namesIndex;
  }

  private static void findReadsBeforeWrites(Instruction[] flow, TIntHashSet[] definitelyAssigned,
                                            List<ReadWriteVariableInstruction> result,
                                            TObjectIntHashMap<String> namesIndex,
                                            int[] postorder,
                                            int[] invpostorder) {
    //skip instructions that are not reachable from the start
    int start = 0;
    while (invpostorder[start] != 0) start++;

    for (int i = start; i < flow.length; i++) {
      int j = invpostorder[i];
      Instruction curr = flow[j];
      if (curr instanceof ReadWriteVariableInstruction) {
        ReadWriteVariableInstruction readWriteInsn = (ReadWriteVariableInstruction) curr;
        int idx = namesIndex.get(readWriteInsn.getVariableName());
        TIntHashSet vars = definitelyAssigned[j];
        if (!readWriteInsn.isWrite()) {
          if (vars == null || !vars.contains(idx)) {
            result.add(readWriteInsn);
          }
        } else {
          if (vars == null) {
            vars = new TIntHashSet();
            definitelyAssigned[j] = vars;
          }
          vars.add(idx);
        }
      }

      for (Instruction succ : curr.allSucc()) {
        if (postorder[succ.num()] > postorder[curr.num()]) {
          TIntHashSet currDefinitelyAssigned = definitelyAssigned[curr.num()];
          TIntHashSet succDefinitelyAssigned = definitelyAssigned[succ.num()];
          if (currDefinitelyAssigned != null) {
            int[] currArray = currDefinitelyAssigned.toArray();
            if (succDefinitelyAssigned == null) {
              succDefinitelyAssigned = new TIntHashSet();
              succDefinitelyAssigned.addAll(currArray);
              definitelyAssigned[succ.num()] = succDefinitelyAssigned;
            } else {
              succDefinitelyAssigned.retainAll(currArray);
            }
          } else {
            if (succDefinitelyAssigned != null) {
              succDefinitelyAssigned.clear();
            } else {
              succDefinitelyAssigned = new TIntHashSet();
              definitelyAssigned[succ.num()] = succDefinitelyAssigned;
            }
          }
        }
      }

    }
  }

}
