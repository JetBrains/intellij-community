/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import java.util.List;
import java.util.ArrayList;

import gnu.trove.TObjectIntHashMap;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectHashMap;
import com.intellij.openapi.diagnostic.Logger;

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
    CallEnvironment env = new CallEnvironment.DepthFirstCallEnvironment();
    for (int i = 0; i < flow.length; i++) {
      if (!visited[i]) {
        N = doVisitForPostorder(flow[i], N, env, result, visited);
      }
    }

    assert N == 0;
    return result;
  }

  private static int doVisitForPostorder(Instruction curr, int currN, CallEnvironment env, int[] postorder, boolean[] visited) {
    visited[curr.num()] = true;
    for (Instruction succ : curr.succ(env)) {
      if (!visited[succ.num()]) {
        currN = doVisitForPostorder(succ, currN, env, postorder, visited);
      }
    }
    postorder[curr.num()] = --currN;
    return currN;
  }

  public static ReadWriteVariableInstruction[] getReadsWithoutPriorWrites(Instruction[] flow) {
    List<ReadWriteVariableInstruction> result = new ArrayList<ReadWriteVariableInstruction>();
    TObjectIntHashMap<String> namesIndex = buildNamesIndex(flow);

    TIntObjectHashMap<TIntHashSet> written = new TIntObjectHashMap<TIntHashSet>(flow.length);

    boolean[] visited = new boolean[flow.length];
    for (int i = 0; i < visited.length; i++) visited[i] = false;

    CallEnvironment env = new CallEnvironment.DepthFirstCallEnvironment();
    for (int i = 0; i < flow.length; i++) {
      if (!visited[i]) doVisitForReadsBeforeWrites(flow[i], written, result, env, namesIndex, visited);
    }

    return result.toArray(new ReadWriteVariableInstruction[result.size()]);
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

  private static void doVisitForReadsBeforeWrites(Instruction curr, TIntObjectHashMap<TIntHashSet> written,
                                                  List<ReadWriteVariableInstruction> result, CallEnvironment env,
                                                  TObjectIntHashMap<String> namesIndex, boolean[] visited) {

    if (curr instanceof ReadWriteVariableInstruction) {
      ReadWriteVariableInstruction readWriteInsn = (ReadWriteVariableInstruction) curr; //do not check for isWrite() to prevent multiple warnings
      int idx = namesIndex.get(readWriteInsn.getVariableName());
      TIntHashSet defs = written.get(idx);
      if (defs == null) {
        defs = new TIntHashSet();
        written.put(idx, defs);
      }
      defs.add(curr.num());
    }

    visited[curr.num()] = true;

    for (Instruction succ : curr.succ(env)) {
      if (succ instanceof ReadWriteVariableInstruction) {
        ReadWriteVariableInstruction readWriteInsn = (ReadWriteVariableInstruction) succ;
        if (!readWriteInsn.isWrite()) {
          int idx = namesIndex.get(readWriteInsn.getVariableName());
          if (!written.contains(idx)) {
            result.add((ReadWriteVariableInstruction) succ);
          }
        }
      }

      if (!visited[succ.num()]) {
        doVisitForReadsBeforeWrites(succ, written, result, env, namesIndex, visited);
      }
    }


    if (curr instanceof ReadWriteVariableInstruction) {
      ReadWriteVariableInstruction readWriteInsn = (ReadWriteVariableInstruction) curr;
      int idx = namesIndex.get(readWriteInsn.getVariableName());
      TIntHashSet defs = written.get(idx);
      LOG.assertTrue(defs != null);
      defs.remove(curr.num());
      if (defs.isEmpty()) {
        written.remove(idx);
      }
    }
  }
}
