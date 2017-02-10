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
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import gnu.trove.TIntHashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrBreakStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.PropertyResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

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
    List<ReadWriteVariableInstruction> result = new ArrayList<>();
    TObjectIntHashMap<String> namesIndex = buildNamesIndex(flow);

    TIntHashSet[] definitelyAssigned = new TIntHashSet[flow.length];

    int[] postorder = postorder(flow);
    int[] invpostorder = invPostorder(postorder);

    findReadsBeforeWrites(flow, definitelyAssigned, result, namesIndex, postorder, invpostorder, onlyFirstRead);
    if (result.isEmpty()) return ReadWriteVariableInstruction.EMPTY_ARRAY;
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
    TObjectIntHashMap<String> namesIndex = new TObjectIntHashMap<>();
    int idx = 0;
    for (Instruction instruction : flow) {
      if (instruction instanceof ReadWriteVariableInstruction) {
        String name = ((ReadWriteVariableInstruction)instruction).getVariableName();
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
        ReadWriteVariableInstruction rw = (ReadWriteVariableInstruction)curr;
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
          right instanceof GrReferenceExpression && findClassByText((GrReferenceExpression)right)) {
        return true;
      }
    }
    return false;
  }

  private static boolean findClassByText(GrReferenceExpression ref) {
    final String text = ref.getText();
    final int i = text.indexOf('<');
    String className = i == -1 ? text : text.substring(0, i);

    PsiClass[] names = PsiShortNamesCache.getInstance(ref.getProject()).getClassesByName(className, ref.getResolveScope());
    if (names.length > 0) return true;

    PsiFile file = ref.getContainingFile();
    if (file instanceof GroovyFile) {
      GrImportStatement[] imports = ((GroovyFile)file).getImportStatements();
      for (GrImportStatement anImport : imports) {
        if (className.equals(anImport.getImportedName())) return true;
      }
    }

    return false;
  }

  /**
   * check whether statement is return (the statement which provides return value) statement of method or closure.
   *
   * @param st
   * @return
   */
  public static boolean isCertainlyReturnStatement(GrStatement st) {
    final PsiElement parent = st.getParent();
    if (parent instanceof GrOpenBlock) {
      if (st != ArrayUtil.getLastElement(((GrOpenBlock)parent).getStatements())) return false;

      PsiElement pparent = parent.getParent();
      if (pparent instanceof GrMethod) {
        return true;
      }

      if (pparent instanceof GrBlockStatement || pparent instanceof GrCatchClause || pparent instanceof GrLabeledStatement) {
        pparent = pparent.getParent();
      }
      if (pparent instanceof GrIfStatement || pparent instanceof GrControlStatement || pparent instanceof GrTryCatchStatement) {
        return isCertainlyReturnStatement((GrStatement)pparent);
      }
    }

    else if (parent instanceof GrClosableBlock) {
      return st == ArrayUtil.getLastElement(((GrClosableBlock)parent).getStatements());
    }

    else if (parent instanceof GroovyFileBase) {
      return st == ArrayUtil.getLastElement(((GroovyFileBase)parent).getStatements());
    }

    else if (parent instanceof GrForStatement ||
             parent instanceof GrIfStatement && st != ((GrIfStatement)parent).getCondition() ||
             parent instanceof GrSynchronizedStatement && st != ((GrSynchronizedStatement)parent).getMonitor() ||
             parent instanceof GrWhileStatement && st != ((GrWhileStatement)parent).getCondition() ||
             parent instanceof GrConditionalExpression && st != ((GrConditionalExpression)parent).getCondition() ||
             parent instanceof GrElvisExpression) {
      return isCertainlyReturnStatement((GrStatement)parent);
    }

    else if (parent instanceof GrCaseSection) {
      final GrStatement[] statements = ((GrCaseSection)parent).getStatements();
      final GrStatement last = ArrayUtil.getLastElement(statements);
      final GrSwitchStatement switchStatement = (GrSwitchStatement)parent.getParent();

      if (last instanceof GrBreakStatement && statements.length > 1 && statements[statements.length - 2] == st) {
        return isCertainlyReturnStatement(switchStatement);
      }
      else if (st == last) {
        if (st instanceof GrBreakStatement || isLastStatementInCaseSection((GrCaseSection)parent, switchStatement)) {
          return isCertainlyReturnStatement(switchStatement);
        }
      }
    }
    return false;
  }

  private static boolean isLastStatementInCaseSection(GrCaseSection caseSection, GrSwitchStatement switchStatement) {
    final GrCaseSection[] sections = switchStatement.getCaseSections();
    final int i = ArrayUtilRt.find(sections, caseSection);
    if (i == sections.length - 1) {
      return true;
    }

    for (int j = i + 1; j < sections.length; j++) {
      GrCaseSection section = sections[j];
      for (GrStatement statement : section.getStatements()) {
        if (!(statement instanceof GrBreakStatement)) {
          return false;
        }
      }
    }
    return true;
  }

  @NotNull
  public static GroovyResolveResult[] resolveNonQualifiedRefWithoutFlow(@NotNull GrReferenceExpression ref) {
    LOG.assertTrue(!ref.isQualified());

    final String referenceName = ref.getReferenceName();
    final ResolverProcessor processor = new PropertyResolverProcessor(referenceName, ref);

    ResolveUtil.treeWalkUp(ref, processor, false);
    final GroovyResolveResult[] candidates = processor.getCandidates();
    if (candidates.length != 0) {
      return candidates;
    }

    return GroovyResolveResult.EMPTY_ARRAY;
  }
}
