// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author Max Medvedev
 */
public final class GrConvertTypeCastToSafeCastIntention extends GrPsiUpdateIntention {
  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    if (!(element instanceof GrTypeCastExpression)) return;

    GrExpression operand = ((GrTypeCastExpression)element).getOperand();
    GrTypeElement type = ((GrTypeCastExpression)element).getCastTypeElement();

    if (type == null) return;
    if (operand == null) return;

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.project());
    GrExpression safeCast = factory.createExpressionFromText(operand.getText() + " as " + type.getText());

    ((GrTypeCastExpression)element).replaceWithExpression(safeCast, true);
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        return element instanceof GrTypeCastExpression &&
               ((GrTypeCastExpression)element).getCastTypeElement() != null &&
               ((GrTypeCastExpression)element).getOperand() != null;
      }
    };
  }
}
