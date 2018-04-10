/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryEnumModifierInspection extends BaseInspection implements CleanupLocalInspectionTool{

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessary.enum.modifier.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiElement parent = (PsiElement)infos[1];
    if (parent instanceof PsiMethod) {
      return InspectionGadgetsBundle.message("unnecessary.enum.modifier.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("unnecessary.enum.modifier.problem.descriptor1");
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryEnumModifierVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryEnumModifierFix((PsiElement)infos[0]);
  }

  private static class UnnecessaryEnumModifierFix extends InspectionGadgetsFix {

    private final String m_name;

    UnnecessaryEnumModifierFix(PsiElement modifier) {
      m_name = InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", modifier.getText());
    }

    @Override
    @NotNull
    public String getName() {
      return m_name;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Remove unnecessary modifiers";
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiModifierList modifierList =
        element instanceof PsiModifierList ? (PsiModifierList)element : (PsiModifierList)element.getParent();
      assert modifierList != null;
      modifierList.setModifierProperty(modifierList.getParent() instanceof PsiClass ? PsiModifier.STATIC : PsiModifier.PRIVATE, false);
    }
  }

  private static class UnnecessaryEnumModifierVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (!aClass.isEnum() || !ClassUtils.isInnerClass(aClass) || !aClass.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiModifierList modifiers = aClass.getModifierList();
      if (modifiers == null) {
        return;
      }
      final PsiElement[] children = modifiers.getChildren();
      for (final PsiElement child : children) {
        final String text = child.getText();
        if (PsiModifier.STATIC.equals(text)) {
          registerError(child, ProblemHighlightType.LIKE_UNUSED_SYMBOL, child, aClass);
        }
      }
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!method.isConstructor() || !method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null || !aClass.isEnum()) {
        return;
      }
      final PsiModifierList modifiers = method.getModifierList();
      final PsiElement[] children = modifiers.getChildren();
      for (final PsiElement child : children) {
        final String text = child.getText();
        if (PsiModifier.PRIVATE.equals(text)) {
          registerError(child, ProblemHighlightType.LIKE_UNUSED_SYMBOL, child, method);
        }
      }
    }
  }
}