/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ipp.types;

import com.intellij.codeInsight.daemon.impl.quickfix.AddMethodBodyFix;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public class MakeMethodDefaultIntention extends BaseElementAtCaretIntentionAction {

  private String text = "Make method default";

  @NotNull
  @Override
  public String getText() {
    return text;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    if (psiMethod != null && PsiUtil.isLanguageLevel8OrHigher(psiMethod)) {
      if (psiMethod.getBody() == null && !psiMethod.hasModifierProperty(PsiModifier.DEFAULT)) {
        final PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass != null && containingClass.isInterface() && !containingClass.isAnnotationType()) {
          text = "Make '" + psiMethod.getName() + "()' default";
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    if (psiMethod != null) {
      final PsiModifierList modifierList = psiMethod.getModifierList();
      modifierList.setModifierProperty(PsiModifier.DEFAULT, true);
      new AddMethodBodyFix(psiMethod).invoke(project, editor, psiMethod.getContainingFile());
    }
  }
}
