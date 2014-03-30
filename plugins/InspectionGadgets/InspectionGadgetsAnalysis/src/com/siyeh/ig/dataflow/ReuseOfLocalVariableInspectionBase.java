/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.dataflow;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReuseOfLocalVariableInspectionBase
  extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "reuse.of.local.variable.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "reuse.of.local.variable.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReuseOfLocalVariableVisitor();
  }

  private static class ReuseOfLocalVariableVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(
      @NotNull PsiAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);
      final PsiElement assignmentParent = assignment.getParent();
      if (!(assignmentParent instanceof PsiExpressionStatement)) {
        return;
      }
      final PsiExpression lhs = assignment.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression reference =
        (PsiReferenceExpression)lhs;
      final PsiElement referent = reference.resolve();
      if (!(referent instanceof PsiLocalVariable)) {
        return;
      }
      final PsiVariable variable = (PsiVariable)referent;

      //TODO: this is safe, but can be weakened
      if (variable.getInitializer() == null) {
        return;
      }
      final IElementType tokenType = assignment.getOperationTokenType();
      if (!JavaTokenType.EQ.equals(tokenType)) {
        return;
      }
      final PsiExpression rhs = assignment.getRExpression();
      if (rhs != null &&
          VariableAccessUtils.variableIsUsed(variable, rhs)) {
        return;
      }
      final PsiCodeBlock variableBlock =
        PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (variableBlock == null) {
        return;
      }

      if (loopExistsBetween(assignment, variableBlock)) {
        return;
      }
      if (tryExistsBetween(assignment, variableBlock)) {
        // this could be weakened, slightly, if it could be verified
        // that a variable is used in only one branch of a try statement
        return;
      }
      final PsiElement assignmentBlock =
        assignmentParent.getParent();
      if (assignmentBlock == null) {
        return;
      }
      if (variableBlock.equals(assignmentBlock)) {
        registerError(lhs);
      }
      final PsiStatement[] statements = variableBlock.getStatements();
      final PsiElement containingStatement =
        getChildWhichContainsElement(variableBlock, assignment);
      int statementPosition = -1;
      for (int i = 0; i < statements.length; i++) {
        if (statements[i].equals(containingStatement)) {
          statementPosition = i;
          break;
        }
      }
      if (statementPosition == -1) {
        return;
      }
      for (int i = statementPosition + 1; i < statements.length; i++) {
        if (VariableAccessUtils.variableIsUsed(variable, statements[i])) {
          return;
        }
      }
      registerError(lhs);
    }

    private static boolean loopExistsBetween(
      PsiAssignmentExpression assignment, PsiCodeBlock block) {
      PsiElement elementToTest = assignment;
      while (elementToTest != null) {
        if (elementToTest.equals(block)) {
          return false;
        }
        if (elementToTest instanceof PsiWhileStatement ||
            elementToTest instanceof PsiForeachStatement ||
            elementToTest instanceof PsiForStatement ||
            elementToTest instanceof PsiDoWhileStatement) {
          return true;
        }
        elementToTest = elementToTest.getParent();
      }
      return false;
    }

    private static boolean tryExistsBetween(
      PsiAssignmentExpression assignment, PsiCodeBlock block) {
      PsiElement elementToTest = assignment;
      while (elementToTest != null) {
        if (elementToTest.equals(block)) {
          return false;
        }
        if (elementToTest instanceof PsiTryStatement) {
          return true;
        }
        elementToTest = elementToTest.getParent();
      }
      return false;
    }

    /**
     * @noinspection AssignmentToMethodParameter
     */
    @Nullable
    public static PsiElement getChildWhichContainsElement(
      @NotNull PsiCodeBlock ancestor,
      @NotNull PsiElement descendant) {
      PsiElement element = descendant;
      while (!element.equals(ancestor)) {
        descendant = element;
        element = descendant.getParent();
        if (element == null) {
          return null;
        }
      }
      return descendant;
    }
  }
}