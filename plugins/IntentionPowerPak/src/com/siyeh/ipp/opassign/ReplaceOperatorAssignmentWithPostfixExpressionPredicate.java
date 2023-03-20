// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.opassign;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.PsiElementPredicate;

class ReplaceOperatorAssignmentWithPostfixExpressionPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiAssignmentExpression assignmentExpression)) {
      return false;
    }
    final IElementType tokenType = assignmentExpression.getOperationTokenType();
    if (!JavaTokenType.PLUSEQ.equals(tokenType) && !JavaTokenType.MINUSEQ.equals(tokenType)) {
      return false;
    }
    final PsiExpression lhs = assignmentExpression.getLExpression();
    if (!TypeConversionUtil.isNumericType(lhs.getType())) {
      return false;
    }
    final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getRExpression())  ;
    return ExpressionUtils.isLiteral(rhs, 1);
  }
}