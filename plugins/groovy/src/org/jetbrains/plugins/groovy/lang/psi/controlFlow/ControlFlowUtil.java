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

import com.intellij.util.containers.HashSet;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/**
 * @author ven
 */
public class ControlFlowUtil {
  public static int[] postorder(Instruction[] flow) {
    int[] result = new int[flow.length];
    boolean[] visited = new boolean[flow.length];
    for (int i = 0; i < result.length; i++) visited[i] = false;

    int N = flow.length;
    CallEnvironment env = new CallEnvironment.DepthFirstCallEnvironment();
    for (int i = 0; i < flow.length; i++) {
      if (!visited[i]) {
        N = doVisitForPostorder(flow[i], flow, N, env, result, visited);
      }
    }

    assert N == 0;
    return result;
  }

  private static int doVisitForPostorder(Instruction curr, Instruction[] flow, int currN, CallEnvironment env, int[] postorder, boolean[] visited) {
    visited[curr.num()] = true;
    for (Instruction succ : curr.succ(env)) {
      if (!visited[succ.num()]) {
        currN = doVisitForPostorder(succ, flow, currN, env, postorder, visited);
      }
    }
    postorder[curr.num()] = --currN;
    return currN;
  }

  //just a single depth-first traversal, no need for DFA
  public static Instruction[] getReadsWithoutPriorWrites(Instruction[] flow) {
    List<Instruction> result = new ArrayList<Instruction>();
    Set<String> written = new HashSet<String>();
    boolean[] visited = new boolean[flow.length];
    for (int i = 0; i < visited.length; i++) visited[i] = false;
    CallEnvironment env = new CallEnvironment.DepthFirstCallEnvironment();
    for (int i = 0; i < flow.length; i++) {
      if (!visited[i]) {
         doVisitForReadsBeforeWrites(flow[i], flow, env, written, result, visited);
      }
    }

    return result.toArray(new Instruction[result.size()]);
  }

  private static void doVisitForReadsBeforeWrites(Instruction curr, Instruction[] flow, CallEnvironment env, Set<String> written, List<Instruction> result, boolean[] visited) {
    visited[curr.num()] = true;

    if (curr instanceof ReadWriteVariableInstruction) {
      ReadWriteVariableInstruction readWriteInsn = (ReadWriteVariableInstruction) curr;
      String name = readWriteInsn.getVariableName();
      if (!readWriteInsn.isWrite() && !written.contains(name)) {
        result.add(curr);
      }

      written.add(name); //do not flag read for the second time
    }

    for (Instruction succ : curr.succ(env)) {
      if (!visited[succ.num()]) {
        doVisitForReadsBeforeWrites(succ, flow, env, written, result, visited);
      }
    }
  }
}
