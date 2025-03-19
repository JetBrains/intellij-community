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
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public final class FlipConjunctionIntention extends GrPsiUpdateIntention {
  @Override
  public @NotNull String getText(@NotNull PsiElement element) {
    final GrBinaryExpression binaryExpression = (GrBinaryExpression)element;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    final String conjunction = getConjunction(tokenType);
    return GroovyIntentionsBundle.message("flip.smth.intention.name", conjunction);
  }

  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return new ConjunctionPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrBinaryExpression exp = (GrBinaryExpression)element;
    final IElementType tokenType = exp.getOperationTokenType();

    final GrExpression lhs = exp.getLeftOperand();
    final String lhsText = lhs.getText();

    final GrExpression rhs = exp.getRightOperand();
    final String rhsText = rhs.getText();

    final String conjunction = getConjunction(tokenType);

    final String newExpression = rhsText + conjunction + lhsText;
    PsiImplUtil.replaceExpression(newExpression, exp);
  }

  private static String getConjunction(IElementType tokenType) {
    return tokenType.equals(GroovyTokenTypes.mLAND) ? "&&" : "||";
  }
}
