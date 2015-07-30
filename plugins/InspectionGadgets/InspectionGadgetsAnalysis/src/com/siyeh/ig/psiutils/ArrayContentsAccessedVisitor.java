/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

class ArrayContentsAccessedVisitor extends JavaRecursiveElementWalkingVisitor {

  private boolean accessed;
  private final PsiVariable variable;

  ArrayContentsAccessedVisitor(@NotNull PsiVariable variable) {
    this.variable = variable;
  }

  @Override
  public void visitForeachStatement(
    @NotNull PsiForeachStatement statement) {
    if (accessed) {
      return;
    }
    super.visitForeachStatement(statement);
    final PsiExpression qualifier = statement.getIteratedValue();
    if (!(qualifier instanceof PsiReferenceExpression)) {
      return;
    }
    final PsiReferenceExpression referenceExpression =
      (PsiReferenceExpression)qualifier;
    final PsiElement target = referenceExpression.resolve();
    if (variable.equals(target)) {
      accessed = true;
    }
  }

  @Override
  public void visitArrayAccessExpression(
    PsiArrayAccessExpression arrayAccessExpression) {
    if (accessed) {
      return;
    }
    super.visitArrayAccessExpression(arrayAccessExpression);
    final PsiElement parent = arrayAccessExpression.getParent();
    if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression =
        (PsiAssignmentExpression)parent;
      final PsiExpression lhs = assignmentExpression.getLExpression();
      if (lhs.equals(arrayAccessExpression)) {
        return;
      }
    }
    final PsiExpression arrayExpression =
      arrayAccessExpression.getArrayExpression();
    if (!(arrayExpression instanceof PsiReferenceExpression)) {
      return;
    }
    final PsiReferenceExpression referenceExpression =
      (PsiReferenceExpression)arrayExpression;
    final PsiElement target = referenceExpression.resolve();
    if (variable.equals(target)) {
      accessed = true;
    }
  }

  @Override
  public void visitReferenceExpression(PsiReferenceExpression expression) {
    if (accessed) {
      return;
    }
    super.visitReferenceExpression(expression);
    @NonNls final String referenceName = expression.getReferenceName();
    if (!"length".equals(referenceName)) {
      return;
    }
    final PsiExpression qualifier = expression.getQualifierExpression();
    if (!(qualifier instanceof PsiReferenceExpression)) {
      return;
    }
    final PsiReferenceExpression referenceExpression =
      (PsiReferenceExpression)qualifier;
    final PsiElement target = referenceExpression.resolve();
    if (variable.equals(target)) {
      accessed = true;
    }
  }

  @Override
  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    if (accessed) {
      return;
    }
    super.visitMethodCallExpression(expression);
    final PsiReferenceExpression methodExpression =
      expression.getMethodExpression();
    final String methodName = methodExpression.getReferenceName();
    if (!HardcodedMethodConstants.CLONE.equals(methodName)) {
      return;
    }
    final PsiExpressionList argumentList = expression.getArgumentList();
    if (argumentList.getExpressions().length != 0) {
      return;
    }
    final PsiExpression qualifier =
      methodExpression.getQualifierExpression();
    if (!(qualifier instanceof PsiReferenceExpression)) {
      return;
    }
    final PsiReferenceExpression referenceExpression =
      (PsiReferenceExpression)qualifier;
    final PsiElement target = referenceExpression.resolve();
    if (variable.equals(target)) {
      accessed = true;
    }
  }

  public boolean isAccessed() {
    return accessed;
  }
}
