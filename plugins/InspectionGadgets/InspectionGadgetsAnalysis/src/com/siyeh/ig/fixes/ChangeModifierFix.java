// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ChangeModifierFix extends InspectionGadgetsFix {

  @PsiModifier.ModifierConstant private final String modifierText;

  public ChangeModifierFix(@NonNls @PsiModifier.ModifierConstant String modifierText) {
    this.modifierText = modifierText;
  }

  @Override
  @NotNull
  public String getName() {
    return PsiModifier.PACKAGE_LOCAL.equals(modifierText)
           ? InspectionGadgetsBundle.message("change.modifier.package.private.quickfix")
           : InspectionGadgetsBundle.message("change.modifier.quickfix", modifierText);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("change.modifier.fix.family.name");
  }

  @Override
  public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiModifierListOwner modifierListOwner = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class);
    if (modifierListOwner == null) {
      return;
    }
    final PsiModifierList modifiers = modifierListOwner.getModifierList();
    if (modifiers == null) {
      return;
    }
    modifiers.setModifierProperty(modifierText, true);
  }
}