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

    while ((element = PsiTreeUtil.getParentOfType(element, PsiParenthesizedExpression.class)) != null) {
      PsiParenthesizedExpression expression = (PsiParenthesizedExpression)element;
      if (isSupportedInnerExpression(expression) && isSupportedOuterExpression(expression)) return true;
    }

    return false;
  }

  private boolean isSupportedInnerExpression(@Nullable PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);

    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (ArrayUtil.contains(tokenType, supportedOperations)) {
        return true;
      }
    }

    return false;
  }

  private boolean isSupportedOuterExpression(@NotNull PsiParenthesizedExpression expression) {
    if (!ParenthesesUtils.areParenthesesNeeded(expression, false)) return false;

    PsiExpression innerExpr = expression;
    while (innerExpr.getParent() instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)innerExpr.getParent();
      if (!ArrayUtil.contains(prefixExpression.getOperationTokenType(), prefixes)) return false;

      innerExpr = prefixExpression;
    }

    final PsiElement parent = innerExpr.getParent();
    if (parent instanceof PsiTypeCastExpression) return false;

    final PsiPolyadicExpression polyadicExpression = ObjectUtils.tryCast(parent, PsiPolyadicExpression.class);
    return polyadicExpression == null ||
           polyadicExpression.getOperands().length >= 2 &&
           ArrayUtil.contains(polyadicExpression.getOperationTokenType(), supportedOperations);
  }
}
