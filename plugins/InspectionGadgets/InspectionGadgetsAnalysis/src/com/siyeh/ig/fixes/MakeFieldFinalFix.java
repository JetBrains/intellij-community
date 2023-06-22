// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.FinalUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MakeFieldFinalFix extends PsiUpdateModCommandQuickFix {

  private final String fieldName;

  private MakeFieldFinalFix(String fieldName) {
    this.fieldName = fieldName;
  }

  @Nullable
  public static LocalQuickFix buildFix(PsiField field) {
    if (!FinalUtils.canBeFinal(field)) {
      return null;
    }
    final String name = field.getName();
    return new MakeFieldFinalFix(name);
  }

  @NotNull
  public static LocalQuickFix buildFixUnconditional(PsiField field) {
    return new MakeFieldFinalFix(field.getName());
  }

  @Override
  @NotNull
  public String getName() {
    return InspectionGadgetsBundle.message("make.field.final.quickfix",
                                           fieldName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("make.field.final.fix.family.name");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiField field;
    if (element instanceof PsiReferenceExpression referenceExpression) {
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiField)) {
        return;
      }
      field = (PsiField)target;
    }
    else {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiField)) {
        return;
      }
      field = (PsiField)parent;
    }
    field.normalizeDeclaration();
    final PsiModifierList modifierList = field.getModifierList();
    if (modifierList == null) {
      return;
    }
    modifierList.setModifierProperty(PsiModifier.VOLATILE, false);
    modifierList.setModifierProperty(PsiModifier.FINAL, true);
  }
}