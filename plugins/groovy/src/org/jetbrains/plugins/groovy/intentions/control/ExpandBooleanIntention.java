// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public final class ExpandBooleanIntention extends GrPsiUpdateIntention {


  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return new ExpandBooleanPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrStatement containingStatement = (GrStatement)element;
    if (ExpandBooleanPredicate.isBooleanAssignment(containingStatement)) {
      final GrAssignmentExpression assignmentExpression = (GrAssignmentExpression)containingStatement;
      final GrExpression rhs = assignmentExpression.getRValue();
      assert rhs != null;
      final String rhsText = rhs.getText();
      final GrExpression lhs = assignmentExpression.getLValue();
      final String lhsText = lhs.getText();
      final @NonNls String statement = "if(" + rhsText + "){\n" + lhsText + " = true\n}else{\n" + lhsText + " = false\n}";
      PsiImplUtil.replaceStatement(statement, containingStatement);
    }
    else if (ExpandBooleanPredicate.isBooleanReturn(containingStatement)) {
      final GrReturnStatement returnStatement = (GrReturnStatement)containingStatement;
      final GrExpression returnValue = returnStatement.getReturnValue();
      final String valueText = returnValue.getText();
      final @NonNls String statement = "if(" + valueText + "){\nreturn true\n}else{\nreturn false\n}";
      PsiImplUtil.replaceStatement(statement, containingStatement);
    }
  }
}
