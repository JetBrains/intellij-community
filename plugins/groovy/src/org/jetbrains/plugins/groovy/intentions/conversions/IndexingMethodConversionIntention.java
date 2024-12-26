// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public final class IndexingMethodConversionIntention extends GrPsiUpdateIntention {

  @Override
  public @NotNull PsiElementPredicate getElementPredicate() {
    return new IndexingMethodConversionPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrMethodCallExpression callExpression =
      (GrMethodCallExpression)element;
    final GrArgumentList argList = callExpression.getArgumentList();
    final GrExpression[] arguments = argList.getExpressionArguments();

    GrReferenceExpression methodExpression = (GrReferenceExpression)callExpression.getInvokedExpression();

    final String methodName = methodExpression.getReferenceName();
    final GrExpression qualifier = methodExpression.getQualifierExpression();
    if ("getAt".equals(methodName) || "get".equals(methodName)) {
      PsiImplUtil.replaceExpression(qualifier.getText() + '[' + arguments[0].getText() + ']',
                                    callExpression);
    }
    else {
      PsiImplUtil.replaceExpression(qualifier.getText() + '[' + arguments[0].getText() + "]=" + arguments[1].getText(),
                                    callExpression);
    }
  }
}
