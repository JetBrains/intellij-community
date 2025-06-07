// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ControlFlowBuilderUtil;

/**
 * @author Max Medvedev
 */
public class RemoveUnnecessaryReturnIntention extends GrPsiUpdateIntention {
  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    if (element instanceof GrReturnStatement returnStatement && returnStatement.getReturnValue() != null) {
      GrExpression value = returnStatement.getReturnValue();
      returnStatement.replaceWithStatement(value);
    }
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return element -> element instanceof GrReturnStatement returnStatement &&
                      returnStatement.getReturnValue() != null &&
                      ControlFlowBuilderUtil.isCertainlyReturnStatement(returnStatement);
  }
}
