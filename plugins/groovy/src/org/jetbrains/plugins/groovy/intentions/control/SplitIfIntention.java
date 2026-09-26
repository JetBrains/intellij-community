// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author Brice Dutheil
 * @author Hamlet D'Arcy
 */
public final class SplitIfIntention extends GrPsiUpdateIntention {

  @Override
  protected void processIntention(@NotNull PsiElement andElement, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    GrBinaryExpression binaryExpression = (GrBinaryExpression) andElement.getParent();
    GrIfStatement ifStatement = (GrIfStatement) binaryExpression.getParent();

    GrExpression leftOperand = binaryExpression.getLeftOperand();
    GrExpression rightOperand = binaryExpression.getRightOperand();

    GrStatement thenBranch = ifStatement.getThenBranch();

    assert thenBranch != null;
    assert rightOperand != null;
    GrStatement newSplittedIfs = GroovyPsiElementFactory.getInstance(context.project())
      .createStatementFromText(
        "if(" + leftOperand.getText() +
           ") { \n" +
           "  if(" + rightOperand.getText() + ")" +
           thenBranch.getText() + "\n" +
           "}"
      );

    ifStatement.replaceWithStatement(newSplittedIfs);
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return element -> element.getParent() instanceof GrBinaryExpression binOp &&
                      binOp.getRightOperand() != null &&
                      binOp.getParent() instanceof GrIfStatement ifStatement &&
                      ifStatement.getElseBranch() == null
                      && "&&".equals(element.getText());
  }
}
