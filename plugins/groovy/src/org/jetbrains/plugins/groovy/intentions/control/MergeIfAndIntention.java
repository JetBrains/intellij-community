// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.ConditionalUtils;
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils;

public final class MergeIfAndIntention extends GrPsiUpdateIntention {


  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return new MergeIfAndPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrIfStatement parentStatement =
        (GrIfStatement) element;
    final GrStatement parentThenBranch = parentStatement.getThenBranch();
    final GrIfStatement childStatement =
        (GrIfStatement) ConditionalUtils.stripBraces(parentThenBranch);

    final GrExpression childCondition = childStatement.getCondition();
    final String childConditionText;
    if (ParenthesesUtils.getPrecedence(childCondition)
        > ParenthesesUtils.AND_PRECEDENCE) {
      childConditionText = '(' + childCondition.getText() + ')';
    } else {
      childConditionText = childCondition.getText();
    }

    final GrExpression parentCondition = parentStatement.getCondition();
    final String parentConditionText;
    if (ParenthesesUtils.getPrecedence(parentCondition)
        > ParenthesesUtils.AND_PRECEDENCE) {
      parentConditionText = '(' + parentCondition.getText() + ')';
    } else {
      parentConditionText = parentCondition.getText();
    }

    final GrStatement childThenBranch = childStatement.getThenBranch();
    final @NonNls String statement =
        "if(" + parentConditionText + "&&" + childConditionText + ')' +
            childThenBranch.getText();
    PsiImplUtil.replaceStatement(statement, parentStatement);
  }
}
