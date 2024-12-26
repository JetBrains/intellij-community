// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public final class MergeElseIfIntention extends GrPsiUpdateIntention {

  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return new MergeElseIfPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrIfStatement parentStatement = (GrIfStatement) element;
    GrBlockStatement elseBlockStatement = (GrBlockStatement) parentStatement.getElseBranch();
    assert elseBlockStatement != null;
    final GrOpenBlock elseBranch = elseBlockStatement.getBlock();
    final GrStatement elseBranchContents = elseBranch.getStatements()[0];
    PsiImplUtil.replaceStatement("if(" +
                                 parentStatement.getCondition().getText() +
                                 ")" +
                                 parentStatement.getThenBranch().getText() +
                                 "else " +
                                 elseBranchContents.getText(), parentStatement);
  }
}
