// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.types;

import com.intellij.codeInsight.daemon.impl.quickfix.AddMethodBodyFix;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MakeMethodDefaultIntention extends PsiBasedModCommandAction<PsiMethod> {
  public MakeMethodDefaultIntention() {
    super(PsiMethod.class);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return IntentionPowerPackBundle.message("make.method.default.family.name");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethod psiMethod) {
    if (PsiUtil.isLanguageLevel8OrHigher(psiMethod)) {
      if (psiMethod.getBody() == null && !psiMethod.hasModifierProperty(PsiModifier.DEFAULT)) {
        final PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass != null && containingClass.isInterface() && !containingClass.isAnnotationType()) {
          return Presentation.of(IntentionPowerPackBundle.message("intention.name.make.default", psiMethod.getName()));
        }
      }
    }
    return null;
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiMethod psiMethod) {
    return new AddMethodBodyFix(psiMethod).perform(context);
  }
}
