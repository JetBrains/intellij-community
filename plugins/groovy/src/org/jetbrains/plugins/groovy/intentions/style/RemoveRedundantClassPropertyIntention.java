// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * @author Max Medvedev
 */
public class RemoveRedundantClassPropertyIntention extends GrPsiUpdateIntention {
  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    if (element instanceof GrReferenceExpression ref) {
      ref.replaceWithExpression(ref.getQualifier(), true);
    }
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return element -> element instanceof GrReferenceExpression ref && "class".equals(ref.getReferenceName()) &&
                  ref.getQualifier() instanceof GrReferenceExpression qualifier && qualifier.resolve() instanceof PsiClass;
  }
}
