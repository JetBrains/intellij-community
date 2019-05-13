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
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class UnnecessaryInterfaceModifierInspection extends BaseInspection implements CleanupLocalInspectionTool{

  private static final Set<String> INTERFACE_REDUNDANT_MODIFIERS =
    new HashSet<>(Arrays.asList(PsiModifier.ABSTRACT, PsiModifier.STATIC));
  private static final Set<String> INNER_CLASS_REDUNDANT_MODIFIERS =
    new HashSet<>(Arrays.asList(PsiModifier.PUBLIC, PsiModifier.STATIC));
  private static final Set<String> INNER_INTERFACE_REDUNDANT_MODIFIERS =
    new HashSet<>(Arrays.asList(PsiModifier.PUBLIC, PsiModifier.ABSTRACT, PsiModifier.STATIC));
  private static final Set<String> FIELD_REDUNDANT_MODIFIERS =
    new HashSet<>(Arrays.asList(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL));
  private static final Set<String> METHOD_REDUNDANT_MODIFIERS =
    new HashSet<>(Arrays.asList(PsiModifier.PUBLIC, PsiModifier.ABSTRACT));

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessary.interface.modifier.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiModifierList modifierList = (PsiModifierList)infos[1];
    final PsiElement parent = modifierList.getParent();
    if (parent instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)parent;
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass != null) {
        if (aClass.isInterface()) {
          return InspectionGadgetsBundle.message("unnecessary.interface.modifier.inner.interface.of.interface.problem.descriptor");
        }
        else {
          return InspectionGadgetsBundle.message("unnecessary.interface.modifier.problem.descriptor3");
        }
      }
      else {
        return InspectionGadgetsBundle.message("unnecessary.interface.modifier.problem.descriptor");
      }
    }
    else if (parent instanceof PsiMethod) {
      return InspectionGadgetsBundle.message("unnecessary.interface.modifier.problem.descriptor2");
    }
    else {
      return InspectionGadgetsBundle.message("unnecessary.interface.modifier.problem.descriptor4");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryInterfaceModifierVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryInterfaceModifiersFix((String)infos[0]);
  }

  private static class UnnecessaryInterfaceModifiersFix extends InspectionGadgetsFix {

    private final String modifiersText;

    private UnnecessaryInterfaceModifiersFix(String modifiersText) {
      this.modifiersText = modifiersText;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", modifiersText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Remove unnecessary modifiers";
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiModifierList modifierList;
      if (element instanceof PsiModifierList) {
        modifierList = (PsiModifierList)element;
      }
      else {
        final PsiElement parent = element.getParent();
        if (!(parent instanceof PsiModifierList)) {
          return;
        }
        modifierList = (PsiModifierList)parent;
      }
      final PsiElement modifierOwner = modifierList.getParent();
      if (!(modifierOwner instanceof PsiMethod && PsiUtil.isLanguageLevel8OrHigher(modifierList))) {
        modifierList.setModifierProperty(PsiModifier.STATIC, false);
      }
      assert modifierOwner != null;
      if (modifierOwner instanceof PsiClass) {
        final PsiClass aClass = (PsiClass)modifierOwner;
        if (aClass.isInterface()) {
          modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
        }
        final PsiClass containingClass = ClassUtils.getContainingClass(modifierOwner);
        if (containingClass != null && containingClass.isInterface()) {
          // do the inner classes
          modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
        }
      }
      else if (modifierOwner instanceof PsiMethod) {
        modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
        modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
      }
      else {
        modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
        modifierList.setModifierProperty(PsiModifier.FINAL, false);
      }
    }
  }

  private static class UnnecessaryInterfaceModifierVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      final PsiClass parent = ClassUtils.getContainingClass(aClass);
      if (parent != null && parent.isInterface()) {
        final PsiModifierList modifiers = aClass.getModifierList();
        if (aClass.isInterface()) {
          checkForRedundantModifiers(modifiers, INNER_INTERFACE_REDUNDANT_MODIFIERS);
        }
        else {
          checkForRedundantModifiers(modifiers, INNER_CLASS_REDUNDANT_MODIFIERS);
        }
      }
      else if (aClass.isInterface()) {
        final PsiModifierList modifiers = aClass.getModifierList();
        checkForRedundantModifiers(modifiers, INTERFACE_REDUNDANT_MODIFIERS);
      }
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      // don't call super, to keep this from drilling in
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!containingClass.isInterface()) {
        return;
      }
      final PsiModifierList modifiers = field.getModifierList();
      checkForRedundantModifiers(modifiers, FIELD_REDUNDANT_MODIFIERS);
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      // don't call super, to keep this from drilling in
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (!aClass.isInterface()) {
        return;
      }
      final PsiModifierList modifiers = method.getModifierList();
      checkForRedundantModifiers(modifiers, METHOD_REDUNDANT_MODIFIERS);
    }

    public void checkForRedundantModifiers(PsiModifierList list, Set<String> modifiers) {
      if (list == null) {
        return;
      }
      final PsiElement[] children = list.getChildren();
      final StringBuilder redundantModifiers = new StringBuilder();
      for (PsiElement child : children) {
        final String modifierText = child.getText();
        if (modifiers.contains(modifierText)) {
          if (redundantModifiers.length() > 0) {
            redundantModifiers.append(' ');
          }
          redundantModifiers.append(modifierText);
        }
      }
      for (PsiElement child : children) {
        if (modifiers.contains(child.getText())) {
          registerError(child, ProblemHighlightType.LIKE_UNUSED_SYMBOL, redundantModifiers.toString(), list);
        }
      }
    }
  }
}
