// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
class ReplaceWithCloneFix extends PsiUpdateModCommandQuickFix {

  private final String myName;

  ReplaceWithCloneFix(String name) {
    myName = name;
  }

  @NotNull
  @Override
  public String getName() {
    return CommonQuickFixBundle.message("fix.replace.with.x", myName + ".clone()");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return CommonQuickFixBundle.message("fix.replace.with.x", "clone()");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (!(element instanceof PsiReferenceExpression referenceExpression)) {
      return;
    }
    if (referenceExpression.getType() instanceof PsiArrayType) {
      PsiReplacementUtil.replaceExpression(referenceExpression, referenceExpression.getText() + ".clone()");
    }
    else {
      final String type =
        TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_DATE, CommonClassNames.JAVA_UTIL_CALENDAR);
      if (type == null) {
        return;
      }
      PsiReplacementUtil.replaceExpression(referenceExpression, '(' + type + ')' + referenceExpression.getText() + ".clone()");
    }
  }
}
