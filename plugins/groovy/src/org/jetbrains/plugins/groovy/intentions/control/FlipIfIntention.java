// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

/**
 * @author Max Medvedev
 */
public final class FlipIfIntention extends GrPsiUpdateIntention {
  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrIfStatement ifStatement = (GrIfStatement)element.getParent();
    final GrIfStatement elseIf = getElseIf(ifStatement);
    final GrIfStatement elseIfCopy = (GrIfStatement)elseIf.copy();

    elseIf.getCondition().replaceWithExpression(ifStatement.getCondition(), true);
    elseIf.getThenBranch().replaceWithStatement(ifStatement.getThenBranch());

    ifStatement.getCondition().replaceWithExpression(elseIfCopy.getCondition(), true);
    ifStatement.getThenBranch().replaceWithStatement(elseIfCopy.getThenBranch());
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        if (!element.getNode().getElementType().equals(GroovyTokenTypes.kIF)) return false;

        final PsiElement parent = element.getParent();
        if (!(parent instanceof GrIfStatement ifStatement)) return false;

        final GrIfStatement elseIf = getElseIf(ifStatement);
        return elseIf != null && checkIf(ifStatement) && checkIf(elseIf);
      }
    };
  }

  private static GrIfStatement getElseIf(GrIfStatement ifStatement) {
    final GrStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch == null) return null;

    if (elseBranch instanceof GrIfStatement) {
      return (GrIfStatement)elseBranch;
    }
    else {
      return null;
    }
  }

  private static boolean checkIf(GrIfStatement ifStatement) {
    return ifStatement.getCondition() != null && ifStatement.getThenBranch() != null;
  }
}
