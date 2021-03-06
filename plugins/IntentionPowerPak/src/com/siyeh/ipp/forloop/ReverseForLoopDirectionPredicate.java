// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.forloop;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ReverseForLoopDirectionPredicate implements PsiElementPredicate {

  @Override
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
    if (variable.getInitializer() == null) {
      return false;
    }
    final PsiType type = variable.getType();
    if (!PsiType.INT.equals(type) && !PsiType.LONG.equals(type)) {
      return false;
    }
    final PsiExpression condition = PsiUtil.skipParenthesizedExprDown(forStatement.getCondition());
    if (!isVariableCompared(variable, condition)) {
      return false;
    }
    final PsiStatement update = forStatement.getUpdate();
    return isVariableIncrementOrDecremented(variable, update);
  }

  public static boolean isVariableCompared(@NotNull PsiVariable variable, @Nullable PsiExpression expression) {
    if (!(expression instanceof PsiBinaryExpression)) {
      return false;
    }
    final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    if (!ComparisonUtils.isComparisonOperation(tokenType)) {
      return false;
    }
    final PsiExpression lhs = binaryExpression.getLOperand();
    final PsiExpression rhs = binaryExpression.getROperand();
    if (rhs == null) {
      return false;
    }
    if (ExpressionUtils.isReferenceTo(lhs, variable)) {
      return true;
    }
    else if (ExpressionUtils.isReferenceTo(rhs, variable)) {
      return true;
    }
    return false;
  }

  public static boolean isVariableIncrementOrDecremented(@NotNull PsiVariable variable, @Nullable PsiStatement statement) {
    if (!(statement instanceof PsiExpressionStatement)) {
      return false;
    }
    final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
    final PsiExpression expression = PsiUtil.skipParenthesizedExprDown(expressionStatement.getExpression());
    if (expression instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
      final IElementType tokenType = prefixExpression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
          !tokenType.equals(JavaTokenType.MINUSMINUS)) {
        return false;
      }
      final PsiExpression operand = prefixExpression.getOperand();
      return ExpressionUtils.isReferenceTo(operand, variable);
    }
    else if (expression instanceof PsiPostfixExpression) {
      final PsiPostfixExpression postfixExpression = (PsiPostfixExpression)expression;
      final IElementType tokenType = postfixExpression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
          !tokenType.equals(JavaTokenType.MINUSMINUS)) {
        return false;
      }
      final PsiExpression operand = postfixExpression.getOperand();
      return ExpressionUtils.isReferenceTo(operand, variable);
    }
    else if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
      final IElementType tokenType = assignmentExpression.getOperationTokenType();
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
      if (!ExpressionUtils.isReferenceTo(lhs, variable)) {
        return false;
      }
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getRExpression());
      if (rhs == null) {
        return false;
      }
      if (tokenType == JavaTokenType.EQ) {
        if (!(rhs instanceof PsiBinaryExpression)) {
          return false;
        }
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)rhs;
        final IElementType token = binaryExpression.getOperationTokenType();
        if (!token.equals(JavaTokenType.PLUS) && !token.equals(JavaTokenType.MINUS)) {
          return false;
        }
        final PsiExpression lOperand = PsiUtil.skipParenthesizedExprDown(binaryExpression.getLOperand());
        final PsiExpression rOperand = PsiUtil.skipParenthesizedExprDown(binaryExpression.getROperand());
        return ExpressionUtils.isReferenceTo(rOperand, variable) || ExpressionUtils.isReferenceTo(lOperand, variable);
      }
      else if (tokenType == JavaTokenType.PLUSEQ || tokenType == JavaTokenType.MINUSEQ) {
        return true;
      }
    }
    return false;
  }
}