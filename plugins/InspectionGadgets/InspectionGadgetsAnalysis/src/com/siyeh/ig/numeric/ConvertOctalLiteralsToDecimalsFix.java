// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
class ConvertOctalLiteralsToDecimalsFix extends PsiUpdateModCommandQuickFix {

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("convert.octal.literals.to.decimal.literals.quickfix");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (!(element instanceof PsiArrayInitializerExpression arrayInitializerExpression)) {
      return;
    }
    for (PsiExpression initializer : arrayInitializerExpression.getInitializers()) {
      initializer = PsiUtil.skipParenthesizedExprDown(initializer);
      if (!(initializer instanceof PsiLiteralExpression literal)) {
        continue;
      }
      if (!ExpressionUtils.isOctalLiteral(literal)) {
        continue;
      }
      ConvertOctalLiteralToDecimalFix.replaceWithDecimalLiteral(literal);
    }
  }
}
