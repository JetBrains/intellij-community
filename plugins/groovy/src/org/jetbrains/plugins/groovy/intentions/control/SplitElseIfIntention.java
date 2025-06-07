// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

public final class SplitElseIfIntention extends GrPsiUpdateIntention {

  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return new SplitElseIfPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrIfStatement parentStatement = (GrIfStatement) element;
    final GrStatement elseBranch = parentStatement.getElseBranch();

    GrIfStatement ifStatement = (GrIfStatement)parentStatement.copy();

    GrBlockStatement blockStatement = GroovyPsiElementFactory.getInstance(context.project())
      .createBlockStatementFromText("{\nabc()\n}", null);
    GrBlockStatement newBlock = ifStatement.replaceElseBranch(blockStatement);

    newBlock.getBlock().getStatements()[0].replace(elseBranch);

    parentStatement.replaceWithStatement(ifStatement);
  }
}
