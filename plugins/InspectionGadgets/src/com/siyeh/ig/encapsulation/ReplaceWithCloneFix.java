// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
class ReplaceWithCloneFix extends InspectionGadgetsFix {

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
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof PsiReferenceExpression)) {
      return;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
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
