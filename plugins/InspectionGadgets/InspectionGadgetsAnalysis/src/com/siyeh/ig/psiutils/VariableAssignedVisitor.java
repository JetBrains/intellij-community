/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class VariableAssignedVisitor extends JavaRecursiveElementWalkingVisitor {

  @NotNull private final Collection<? extends PsiVariable> variables;
  private final boolean recurseIntoClasses;
  private final boolean checkUnaryExpressions;
  private boolean assigned = false;
  private PsiElement excludedElement = null;

  public VariableAssignedVisitor(@NotNull Collection<? extends PsiVariable> variables, boolean recurseIntoClasses) {
    this.variables = variables;
    checkUnaryExpressions = true;
    this.recurseIntoClasses = recurseIntoClasses;
  }

  public VariableAssignedVisitor(@NotNull PsiVariable variable, boolean recurseIntoClasses) {
    variables = Collections.singleton(variable);
    final PsiType type = variable.getType();
    checkUnaryExpressions = TypeConversionUtil.isNumericType(type);
    this.recurseIntoClasses = recurseIntoClasses;
  }

  public VariableAssignedVisitor(@NotNull PsiVariable variable) {
    this(variable, true);
  }

  public void setExcludedElement(PsiElement excludedElement) {
    this.excludedElement = excludedElement;
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (assigned || element == excludedElement) {
      return;
    }
    super.visitElement(element);
  }

  @Override
  public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
    if (assigned) {
      return;
    }
    super.visitAssignmentExpression(assignment);
    final PsiExpression lhs = assignment.getLExpression();
    for (PsiVariable variable : variables) {
      if (ExpressionUtils.isReferenceTo(lhs, variable)) {
        assigned = true;
        break;
      }
    }
  }

  @Override
  public void visitClass(PsiClass aClass) {
    if (!recurseIntoClasses || assigned) {
      return;
    }
    super.visitClass(aClass);
  }

  @Override
  public void visitUnaryExpression(@NotNull PsiUnaryExpression prefixExpression) {
    if (assigned) {
      return;
    }
    super.visitUnaryExpression(prefixExpression);
    if (!checkUnaryExpressions) {
      return;
    }
    final IElementType tokenType = prefixExpression.getOperationTokenType();
    if (!tokenType.equals(JavaTokenType.PLUSPLUS) && !tokenType.equals(JavaTokenType.MINUSMINUS)) {
      return;
    }
    final PsiExpression operand = prefixExpression.getOperand();
    for (PsiVariable variable : variables) {
      if (ExpressionUtils.isReferenceTo(operand, variable)) {
        assigned = true;
        break;
      }
    }
  }

  public boolean isAssigned() {
    return assigned;
  }
}