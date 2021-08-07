// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RemoveModifierFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryRecordModifierInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.redundant.modifier.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryRecordModifierVisitor();
  }

  @Override
  protected @Nullable InspectionGadgetsFix buildFix(Object... infos) {
    return new RemoveModifierFix((String)infos[0]);
  }

  private static class UnnecessaryRecordModifierVisitor extends BaseInspectionVisitor {
    @Override
    public void visitClass(PsiClass aClass) {
      if (!aClass.isRecord()) return;
      PsiModifierList modifiers = aClass.getModifierList();
      if (modifiers == null) return;
      for (PsiElement modifier : modifiers.getChildren()) {
        String modifierText = modifier.getText();
        if (PsiModifier.FINAL.equals(modifierText) || PsiModifier.STATIC.equals(modifierText) && !PsiUtil.isLocalClass(aClass)) {
          registerError(modifier, ProblemHighlightType.LIKE_UNUSED_SYMBOL, modifierText);
        }
      }
    }
  }
}
