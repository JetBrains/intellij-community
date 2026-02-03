// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.ComparisonUtils;

public final class FlipComparisonIntention extends GrPsiUpdateIntention {
  @Override
  public @NotNull String getText(@NotNull PsiElement element) {
    final GrBinaryExpression binaryExpression = (GrBinaryExpression) element;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    final String comparison = ComparisonUtils.getStringForComparison(tokenType);
    final String flippedComparison = ComparisonUtils.getFlippedComparison(tokenType);

    if (comparison.equals(flippedComparison)) {
      return GroovyIntentionsBundle.message("flip.smth.intention.name", comparison);
    }

    return GroovyIntentionsBundle.message("flip.comparison.intention.name", comparison, flippedComparison);
  }

  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return new ComparisonPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrBinaryExpression exp =
        (GrBinaryExpression) element;
    final IElementType tokenType = exp.getOperationTokenType();

    final GrExpression lhs = exp.getLeftOperand();
    final String lhsText = lhs.getText();

    final GrExpression rhs = exp.getRightOperand();
    final String rhsText = rhs.getText();

    final String flippedComparison = ComparisonUtils.getFlippedComparison(tokenType);

    final String newExpression =
        rhsText + flippedComparison + lhsText;
    PsiImplUtil.replaceExpression(newExpression, exp);
  }

}
