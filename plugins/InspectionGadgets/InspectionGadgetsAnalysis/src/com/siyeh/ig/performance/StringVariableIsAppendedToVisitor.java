/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

class StringVariableIsAppendedToVisitor extends JavaRecursiveElementWalkingVisitor {
  private boolean appendedTo;
  private final PsiVariable variable;
  private final boolean onlyWarnOnLoop;

  StringVariableIsAppendedToVisitor(PsiVariable variable,
                                    boolean onlyWarnOnLoop) {
    this.variable = variable;
    this.onlyWarnOnLoop = onlyWarnOnLoop;
  }

  @Override
  public void visitAssignmentExpression(
    @NotNull PsiAssignmentExpression assignment) {
    if (appendedTo) {
      return;
    }
    super.visitAssignmentExpression(assignment);
    final PsiExpression lhs = assignment.getLExpression();
    final PsiExpression rhs = assignment.getRExpression();
    if (rhs == null) {
      return;
    }
    if (!(lhs instanceof PsiReferenceExpression)) {
      return;
    }
    final PsiReferenceExpression reference = (PsiReferenceExpression)lhs;
    final PsiExpression qualifier = reference.getQualifierExpression();
    if (qualifier != null) {
      return;
    }
    final PsiElement referent = reference.resolve();
    if (!variable.equals(referent)) {
      return;
    }
    final IElementType tokenType = assignment.getOperationTokenType();
    if (tokenType.equals(JavaTokenType.PLUSEQ)) {
      if (onlyWarnOnLoop && !ControlFlowUtils.isInLoop(assignment)) {
        return;
      }
      appendedTo = true;
    }
    else if (isConcatenation(rhs)) {
      if (onlyWarnOnLoop && !ControlFlowUtils.isInLoop(assignment)) {
        return;
      }
      appendedTo = true;
    }
  }

  private boolean isConcatenation(PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    if (expression instanceof PsiReferenceExpression) {
      final PsiElement referent = ((PsiReference)expression).resolve();
      return variable.equals(referent);
    }
    if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      final PsiExpression body = parenthesizedExpression.getExpression();
      return isConcatenation(body);
    }
    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      for (PsiExpression operand : polyadicExpression.getOperands()) {
        if (isConcatenation(operand)) return true;
      }
    }
    return false;
  }

  boolean isAppendedTo() {
    return appendedTo;
  }
}
