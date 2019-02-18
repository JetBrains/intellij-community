// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.expression;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExpandBracketsPredicate implements PsiElementPredicate {

  private final IElementType[] supportedOperations;
  private final IElementType[] prefixes;

  public ExpandBracketsPredicate(@NotNull IElementType[] operations, @NotNull IElementType[] prefixes) {
    this.supportedOperations = operations;
    this.prefixes = prefixes;
  }

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) return false;

    final PsiParenthesizedExpression expression = PsiTreeUtil.getParentOfType(element, PsiParenthesizedExpression.class);
    if (expression == null) return false;

    return isSupportedInnerExpression(expression) && isSupportedOuterExpression(expression);
  }

  private boolean isSupportedInnerExpression(@Nullable PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);

    if (expression instanceof PsiPrefixExpression ||
        expression instanceof PsiTypeCastExpression ||
        expression instanceof PsiLiteralExpression ||
        expression instanceof PsiReferenceExpression) {
      return true;
    }

    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (ArrayUtil.contains(tokenType, supportedOperations)) {
        return true;
      }
    }

    return false;
  }

  private boolean isSupportedOuterExpression(@NotNull PsiExpression expression) {
    while (expression.getParent() instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression.getParent();
      if (!ArrayUtil.contains(prefixExpression.getOperationTokenType(), prefixes)) {
        return false;
      }

      expression = prefixExpression;
    }

    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiTypeCastExpression) return false;

    final PsiPolyadicExpression polyadicExpression = ObjectUtils.tryCast(parent, PsiPolyadicExpression.class);
    return polyadicExpression == null ||
           polyadicExpression.getOperands().length >= 2 &&
           ArrayUtil.contains(polyadicExpression.getOperationTokenType(), supportedOperations);
  }
}
