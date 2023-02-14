/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RemoveModifierFix;
import org.jetbrains.annotations.NotNull;

public class FinalMethodInFinalClassInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FinalMethodInFinalClassVisitor();
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "final.method.in.final.class.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new RemoveModifierFix(PsiModifier.FINAL);
  }

  private static class FinalMethodInFinalClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!method.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.STATIC) &&
          AnnotationUtil.findAnnotation(method, true, CommonClassNames.JAVA_LANG_SAFE_VARARGS) != null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || containingClass.isEnum()) {
        return;
      }
      if (!containingClass.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final PsiModifierList modifiers = method.getModifierList();
      final PsiElement[] children = modifiers.getChildren();
      for (final PsiElement child : children) {
        final String text = child.getText();
        if (PsiModifier.FINAL.equals(text)) {
          registerError(child);
        }
      }
    }
  }
}