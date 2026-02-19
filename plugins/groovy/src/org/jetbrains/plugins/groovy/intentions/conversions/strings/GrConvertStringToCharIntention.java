// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.conversions.strings;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * @author Max Medvedev
 */
public final class GrConvertStringToCharIntention extends GrPsiUpdateIntention {
  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    final GrExpression cast = GroovyPsiElementFactory.getInstance(context.project()).createExpressionFromText("a as char");
    ((GrSafeCastExpression)cast).getOperand().replaceWithExpression((GrExpression)element, true);

    ((GrExpression)element).replaceWithExpression(cast, true);
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        if (!(element instanceof GrLiteral)) return false;

        final Object value = ((GrLiteral)element).getValue();
        return value instanceof String && ((String)value).length() == 1;
      }
    };
  }
}
