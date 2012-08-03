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
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.util.ArrayUtil;
import gnu.trove.TIntHashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class ControlFlowBuilderUtil {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.controlFlow.ControlFlowBuilderUtil");

  private ControlFlowBuilderUtil() {
  }

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
    for (Instruction succ : curr.allSuccessors()) {
      if (!visited[succ.num()]) {
        currN = doVisitForPostorder(succ, currN, postorder, visited);
      }
    }
    postorder[curr.num()] = --currN;
    return currN;
  }

  public static ReadWriteVariableInstruction[] getReadsWithoutPriorWrites(Instruction[] flow, boolean onlyFirstRead) {
    List<ReadWriteVariableInstruction> result = new ArrayList<ReadWriteVariableInstruction>();
    TObjectIntHashMap<String> namesIndex = buildNamesIndex(flow);

    TIntHashSet[] definitelyAssigned = new TIntHashSet[flow.length];

    int[] postorder = postorder(flow);
    int[] invpostorder = invPostorder(postorder);

    findReadsBeforeWrites(flow, definitelyAssigned, result, namesIndex, postorder, invpostorder, onlyFirstRead);
    if (result.size() == 0) return ReadWriteVariableInstruction.EMPTY_ARRAY;
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
                                            int[] invpostorder,
                                            boolean onlyFirstRead) {
    //skip instructions that are not reachable from the start
    int start = ArrayUtil.find(invpostorder, 0);

    for (int i = start; i < flow.length; i++) {
      int j = invpostorder[i];
      Instruction curr = flow[j];
      if (curr instanceof ReadWriteVariableInstruction) {
        ReadWriteVariableInstruction rw = (ReadWriteVariableInstruction) curr;
        int name = namesIndex.get(rw.getVariableName());
        TIntHashSet vars = definitelyAssigned[j];
        if (rw.isWrite()) {
          if (vars == null) {
            vars = new TIntHashSet();
            definitelyAssigned[j] = vars;
          }
          vars.add(name);
        }
        else {
          if (vars == null || !vars.contains(name)) {
            result.add(rw);
            if (onlyFirstRead) {
              if (vars == null) {
                vars = new TIntHashSet();
                definitelyAssigned[j] = vars;
              }
              vars.add(name);
            }
          }
        }
      }

      for (Instruction succ : curr.allSuccessors()) {
        if (postorder[succ.num()] > postorder[curr.num()]) {
          TIntHashSet currDefinitelyAssigned = definitelyAssigned[curr.num()];
          TIntHashSet succDefinitelyAssigned = definitelyAssigned[succ.num()];
          if (currDefinitelyAssigned != null) {
            int[] currArray = currDefinitelyAssigned.toArray();
            if (succDefinitelyAssigned == null) {
              succDefinitelyAssigned = new TIntHashSet();
              succDefinitelyAssigned.addAll(currArray);
              definitelyAssigned[succ.num()] = succDefinitelyAssigned;
            }
            else {
              succDefinitelyAssigned.retainAll(currArray);
            }
          }
          else {
            if (succDefinitelyAssigned != null) {
              succDefinitelyAssigned.clear();
            }
            else {
              succDefinitelyAssigned = new TIntHashSet();
              definitelyAssigned[succ.num()] = succDefinitelyAssigned;
            }
          }
        }
      }
    }
  }

  public static boolean isInstanceOfBinary(GrBinaryExpression binary) {
    if (binary.getOperationTokenType() == GroovyTokenTypes.kIN) {
      GrExpression left = binary.getLeftOperand();
      GrExpression right = binary.getRightOperand();
      if (left instanceof GrReferenceExpression && ((GrReferenceExpression)left).getQualifier() == null &&
          right instanceof GrReferenceExpression && isClassHeuristic((GrReferenceExpression)right)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isClassHeuristic(GrReferenceExpression ref) {
    if (findClassByText(ref)) {
      return true;
    }

    GrExpression qualifier = ref.getQualifier();
    while (qualifier != null) {
      if (!(qualifier instanceof GrReferenceExpression)) return false;
      qualifier = ((GrReferenceExpression)qualifier).getQualifier();
    }

    final String name = ref.getName();
    if (name == null || Character.isLowerCase(name.charAt(0))) return false;

    return true;
  }

  private static boolean findClassByText(GrReferenceExpression ref) {
    final String text = ref.getText();
    final PsiClass aClass = JavaPsiFacade.getInstance(ref.getProject()).findClass(text, ref.getResolveScope());
    return aClass != null;
  }
}
