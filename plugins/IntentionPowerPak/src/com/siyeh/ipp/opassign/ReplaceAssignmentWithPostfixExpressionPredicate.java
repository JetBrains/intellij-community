// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.opassign;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.PsiElementPredicate;

class ReplaceAssignmentWithPostfixExpressionPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiAssignmentExpression assignmentExpression)) {
      return false;
    }
    final PsiExpression lhs = assignmentExpression.getLExpression();
    final PsiExpression strippedLhs =
      PsiUtil.skipParenthesizedExprDown(lhs);
    if (!(strippedLhs instanceof PsiReferenceExpression referenceExpression)) {
      return false;
    }
    final PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiVariable variable)) {
      return false;
    }
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getRExpression());
    if (!(rhs instanceof PsiBinaryExpression binaryExpression)) {
      return false;
    }
    final PsiExpression lOperand = PsiUtil.skipParenthesizedExprDown(binaryExpression.getLOperand());
    final PsiExpression rOperand = PsiUtil.skipParenthesizedExprDown(binaryExpression.getROperand());
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    if (ExpressionUtils.isLiteral(lOperand, 1)) {
      if (!ExpressionUtils.isReferenceTo(rOperand, variable)) {
        return false;
      }
      return JavaTokenType.PLUS.equals(tokenType);
    }
    else if (ExpressionUtils.isLiteral(rOperand, 1)) {
      if (!ExpressionUtils.isReferenceTo(lOperand, variable)) {
        return false;
      }
      return JavaTokenType.PLUS.equals(tokenType) || JavaTokenType.MINUS.equals(tokenType);
    }
    return false;
  }
}