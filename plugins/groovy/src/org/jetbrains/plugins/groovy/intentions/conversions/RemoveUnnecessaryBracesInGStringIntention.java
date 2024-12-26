// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

/**
 * @author Maxim.Medvedev
 */
public final class RemoveUnnecessaryBracesInGStringIntention extends GrPsiUpdateIntention {
  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return element -> {
      if (!(element instanceof GrString)) return false;

      if (ErrorUtil.containsError(element)) return false;

      for (GrStringInjection child : ((GrString)element).getInjections()) {
        if (GrStringUtil.checkGStringInjectionForUnnecessaryBraces(child)) return true;
      }
      return false;
    };
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    GrStringUtil.removeUnnecessaryBracesInGString((GrString)element);
  }
}


