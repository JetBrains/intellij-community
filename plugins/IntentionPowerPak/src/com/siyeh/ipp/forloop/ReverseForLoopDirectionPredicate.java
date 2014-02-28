/*
 * Copyright 2009-2014 Bas Leijdekkers
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
package com.siyeh.ipp.forloop;

import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ReverseForLoopDirectionPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    final PsiJavaToken keyword = (PsiJavaToken)element;
    final IElementType tokenType = keyword.getTokenType();
    if (!JavaTokenType.FOR_KEYWORD.equals(tokenType)) {
      return false;
    }
    final PsiElement parent = keyword.getParent();
    if (!(parent instanceof PsiForStatement)) {
      return false;
    }
    final PsiForStatement forStatement = (PsiForStatement)parent;
    final PsiStatement initialization = forStatement.getInitialization();
    if (!(initialization instanceof PsiDeclarationStatement)) {
      return false;
    }
    final PsiDeclarationStatement declarationStatement =
      (PsiDeclarationStatement)initialization;
    final PsiElement[] declaredElements =
      declarationStatement.getDeclaredElements();
    if (declaredElements.length != 1) {
      return false;
    }
    final PsiElement declaredElement = declaredElements[0];
    if (!(declaredElement instanceof PsiLocalVariable)) {
      return false;
    }
    final PsiVariable variable = (PsiVariable)declaredElement;
    final PsiType type = variable.getType();
    if (!PsiType.INT.equals(type) && !PsiType.LONG.equals(type)) {
      return false;
    }
    final PsiExpression condition = forStatement.getCondition();
    if (!isVariableCompared(variable, condition)) {
      return false;
    }
    final PsiStatement update = forStatement.getUpdate();
    return isVariableIncrementOrDecremented(variable, update);
  }

  public static boolean isVariableCompared(
    @NotNull PsiVariable variable, @Nullable PsiExpression expression) {
    if (!(expression instanceof PsiBinaryExpression)) {
      return false;
    }
    final PsiBinaryExpression binaryExpression =
      (PsiBinaryExpression)expression;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    if (!ComparisonUtils.isComparisonOperation(tokenType)) {
      return false;
    }
    final PsiExpression lhs = binaryExpression.getLOperand();
    final PsiExpression rhs = binaryExpression.getROperand();
    if (rhs == null) {
      return false;
    }
    if (VariableAccessUtils.evaluatesToVariable(lhs, variable)) {
      return true;
    }
    else if (VariableAccessUtils.evaluatesToVariable(rhs, variable)) {
      return true;
    }
    return false;
  }

  public static boolean isVariableIncrementOrDecremented(
    @NotNull PsiVariable variable, @Nullable PsiStatement statement) {
    if (!(statement instanceof PsiExpressionStatement)) {
      return false;
    }
    final PsiExpressionStatement expressionStatement =
      (PsiExpressionStatement)statement;
    PsiExpression expression = expressionStatement.getExpression();
    expression = ParenthesesUtils.stripParentheses(expression);
    if (expression instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression =
        (PsiPrefixExpression)expression;
      final IElementType tokenType = prefixExpression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
          !tokenType.equals(JavaTokenType.MINUSMINUS)) {
        return false;
      }
      final PsiExpression operand = prefixExpression.getOperand();
      return VariableAccessUtils.evaluatesToVariable(operand, variable);
    }
    else if (expression instanceof PsiPostfixExpression) {
      final PsiPostfixExpression postfixExpression =
        (PsiPostfixExpression)expression;
      final IElementType tokenType = postfixExpression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
          !tokenType.equals(JavaTokenType.MINUSMINUS)) {
        return false;
      }
      final PsiExpression operand = postfixExpression.getOperand();
      return VariableAccessUtils.evaluatesToVariable(operand, variable);
    }
    else if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression =
        (PsiAssignmentExpression)expression;
      final IElementType tokenType =
        assignmentExpression.getOperationTokenType();
      PsiExpression lhs = assignmentExpression.getLExpression();
      lhs = ParenthesesUtils.stripParentheses(lhs);
      if (!VariableAccessUtils.evaluatesToVariable(lhs, variable)) {
        return false;
      }
      PsiExpression rhs = assignmentExpression.getRExpression();
      rhs = ParenthesesUtils.stripParentheses(rhs);
      if (tokenType == JavaTokenType.EQ) {
        if (!(rhs instanceof PsiBinaryExpression)) {
          return false;
        }
        final PsiBinaryExpression binaryExpression =
          (PsiBinaryExpression)rhs;
        final IElementType token =
          binaryExpression.getOperationTokenType();
        if (!token.equals(JavaTokenType.PLUS) &&
            !token.equals(JavaTokenType.MINUS)) {
          return false;
        }
        PsiExpression lOperand = binaryExpression.getLOperand();
        lOperand = ParenthesesUtils.stripParentheses(lOperand);
        PsiExpression rOperand = binaryExpression.getROperand();
        rOperand = ParenthesesUtils.stripParentheses(rOperand);
        if (VariableAccessUtils.evaluatesToVariable(rOperand, variable)) {
          return true;
        }
        else if (VariableAccessUtils.evaluatesToVariable(lOperand, variable)) {
          return true;
        }
      }
      else if (tokenType == JavaTokenType.PLUSEQ ||
               tokenType == JavaTokenType.MINUSEQ) {
        return true;
      }
    }
    return false;
  }
}