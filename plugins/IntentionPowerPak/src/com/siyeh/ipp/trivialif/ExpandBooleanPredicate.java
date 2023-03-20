/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ipp.base.PsiElementPredicate;

class ExpandBooleanPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiStatement statement)) {
      return false;
    }
    final PsiElement lastLeaf = PsiTreeUtil.getDeepestLast(statement);
    if (PsiUtil.isJavaToken(lastLeaf, JavaTokenType.SEMICOLON) && PsiTreeUtil.prevLeaf(lastLeaf) instanceof PsiErrorElement) {
      return false;
    }
    return isBooleanReturn(statement) || isBooleanAssignment(statement) || isBooleanDeclaration(statement);
  }

  public static boolean isBooleanReturn(PsiStatement statement) {
    if (!(statement instanceof PsiReturnStatement returnStatement)) {
      return false;
    }
    final PsiExpression returnValue = PsiUtil.skipParenthesizedExprDown(returnStatement.getReturnValue());
    if (returnValue == null || returnValue instanceof PsiLiteralExpression) {
      return false;
    }
    final PsiType returnType = returnValue.getType();
    return PsiTypes.booleanType().equals(returnType);
  }

  public static boolean isBooleanAssignment(PsiStatement statement) {
    if (!(statement instanceof PsiExpressionStatement expressionStatement)) {
      return false;
    }
    if (expressionStatement.getParent() instanceof PsiSwitchLabeledRuleStatement) {
      return false;
    }
    final PsiExpression expression = expressionStatement.getExpression();
    if (!(expression instanceof PsiAssignmentExpression assignment)) {
      return false;
    }
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(assignment.getRExpression());
    if (rhs == null || rhs instanceof PsiLiteralExpression) {
      return false;
    }
    final PsiType assignmentType = rhs.getType();
    return PsiTypes.booleanType().equals(assignmentType);
  }

  public static boolean isBooleanDeclaration(PsiStatement statement) {
    if (!(statement instanceof PsiDeclarationStatement declarationStatement)) {
      return false;
    }
    final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
    if (declaredElements.length != 1) {
      return false;
    }
    final PsiElement element = declaredElements[0];
    if (!(element instanceof PsiLocalVariable variable)) {
      return false;
    }
    final PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
    if (initializer == null || initializer instanceof PsiLiteralExpression) {
      return false;
    }
    final PsiType type = initializer.getType();
    return PsiTypes.booleanType().equals(type);
  }
}
