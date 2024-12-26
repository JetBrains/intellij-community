// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author Andreas Arledal
 */
public final class ReplaceTernaryWithIfElseIntention extends GrPsiUpdateIntention {

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    GrConditionalExpression parentTernary = findTernary(element);
    GroovyPsiElementFactory groovyPsiElementFactory = GroovyPsiElementFactory.getInstance(context.project());

    GrReturnStatement parentReturn = (GrReturnStatement)parentTernary.getParent();

    String condition = parentTernary.getCondition().getText();
    GrExpression thenBranch = parentTernary.getThenBranch();
    String thenText = thenBranch != null ? thenBranch.getText() : "";

    GrExpression elseBranch = parentTernary.getElseBranch();
    String elseText = elseBranch != null ? elseBranch.getText() : "";

    String text = "if (" + condition + ") { \nreturn " + thenText + "\n} else {\n return " + elseText + "\n}";
    GrIfStatement ifStatement = (GrIfStatement)groovyPsiElementFactory.createStatementFromText(text);
    ifStatement = parentReturn.replaceWithStatement(ifStatement);
    updater.moveCaretTo(ifStatement.getRParenth().getTextRange().getEndOffset());
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return element -> {
      GrConditionalExpression ternary = findTernary(element);
      return ternary != null &&
             ternary.getThenBranch() != null &&
             ternary.getElseBranch() != null &&
             ternary.getParent() instanceof GrReturnStatement;
    };
  }

  private static @Nullable GrConditionalExpression findTernary(PsiElement element) {
    GrConditionalExpression ternary = PsiTreeUtil.getParentOfType(element, GrConditionalExpression.class);
    if (ternary == null) {
      GrReturnStatement ret = PsiTreeUtil.getParentOfType(element, GrReturnStatement.class);
      if (ret != null && ret.getReturnValue() instanceof GrConditionalExpression value) {
        return value;
      }
    }
    return ternary;
  }
}
