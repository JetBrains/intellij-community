// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.closure;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public final class MakeClosureCallExplicitIntention extends GrPsiUpdateIntention {


  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return ImplicitClosureCallPredicate.INSTANCE;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrMethodCallExpression expression =
      (GrMethodCallExpression)element;
    final GrExpression invokedExpression = expression.getInvokedExpression();
    final GrArgumentList argList = expression.getArgumentList();
    final GrClosableBlock[] closureArgs = expression.getClosureArguments();
    final StringBuilder newExpression = new StringBuilder();
    newExpression.append(invokedExpression.getText());
    newExpression.append(".call");
    newExpression.append(argList.getText());
    for (GrClosableBlock closureArg : closureArgs) {
      newExpression.append(closureArg.getText());
    }
    PsiImplUtil.replaceExpression(newExpression.toString(), expression);
  }
}
