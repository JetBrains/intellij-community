// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.bool;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class FlipComparisonPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiBinaryExpression)) {
      return false;
    }
    final PsiBinaryExpression expression = (PsiBinaryExpression)element;
    if (!ComparisonUtils.isComparison(expression)) {
      return false;
    }
    final PsiJavaToken sign = expression.getOperationSign();
    final PsiExpression rhs = expression.getROperand();
    if (rhs == null) {
      return false;
    }
    if (">".equals(sign.getText()) && rhs instanceof PsiReferenceExpression) {
      // would get parsed as type element when flipped and reparsed
      PsiElement parent = PsiTreeUtil.skipParentsOfType(expression, PsiPolyadicExpression.class);
      if (parent instanceof PsiExpressionStatement) {
        return false;
      }
    }
    if (expression.getParent() instanceof PsiAssignmentExpression &&
        ((PsiAssignmentExpression)expression.getParent()).getLExpression() == expression) {
      return false;
    }
    return !expression.getLOperand().getText().equals(rhs.getText()) && !ErrorUtil.containsDeepError(element);
  }
}
