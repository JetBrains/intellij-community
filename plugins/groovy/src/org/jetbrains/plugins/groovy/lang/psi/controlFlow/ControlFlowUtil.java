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

import com.intellij.openapi.util.Ref;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

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
}
