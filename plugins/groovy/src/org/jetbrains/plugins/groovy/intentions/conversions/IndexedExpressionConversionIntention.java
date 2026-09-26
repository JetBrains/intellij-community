// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public final class IndexedExpressionConversionIntention extends GrPsiUpdateIntention {

  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return new IndexedExpressionConversionPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrIndexProperty arrayIndexExpression = (GrIndexProperty)element;

    final GrArgumentList argList = (GrArgumentList)arrayIndexExpression.getLastChild();

    assert argList != null;
    final GrExpression[] arguments = argList.getExpressionArguments();

    final PsiElement parent = element.getParent();
    final GrExpression arrayExpression = arrayIndexExpression.getInvokedExpression();
    if (!(parent instanceof GrAssignmentExpression assignmentExpression)) {
      rewriteAsGetAt(arrayIndexExpression, arrayExpression, arguments[0]);
      return;
    }
    final GrExpression rhs = assignmentExpression.getRValue();
    if (rhs.equals(element)) {
      rewriteAsGetAt(arrayIndexExpression, arrayExpression, arguments[0]);
    }
    else {
      rewriteAsSetAt(assignmentExpression, arrayExpression, arguments[0], rhs);
    }
  }

  private static void rewriteAsGetAt(GrIndexProperty arrayIndexExpression, GrExpression arrayExpression, GrExpression argument)
    throws IncorrectOperationException {
    PsiImplUtil.replaceExpression(arrayExpression.getText() + ".getAt(" + argument.getText() + ')', arrayIndexExpression);
  }

  private static void rewriteAsSetAt(GrAssignmentExpression assignment,
                                     GrExpression arrayExpression,
                                     GrExpression argument,
                                     GrExpression value) throws IncorrectOperationException {
    PsiImplUtil.replaceExpression(arrayExpression.getText() + ".putAt(" + argument.getText() + ", " + value.getText() + ')', assignment);
  }
}
