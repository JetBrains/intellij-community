// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.BoolUtils;

public final class FlipConditionalIntention extends GrPsiUpdateIntention {


  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return new ConditionalPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrConditionalExpression exp =
        (GrConditionalExpression) element;

    final GrExpression condition = exp.getCondition();
    final GrExpression elseExpression = exp.getElseBranch();
    final GrExpression thenExpression = exp.getThenBranch();
    assert elseExpression != null;
    assert thenExpression != null;
    final String newExpression =
        BoolUtils.getNegatedExpressionText(condition) + '?' +
            elseExpression.getText() +
            ':' +
            thenExpression.getText();
    PsiImplUtil.replaceExpression(newExpression, exp);
  }

}
