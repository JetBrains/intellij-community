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
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import org.jetbrains.annotations.NotNull;

public class UtilityClassWithPublicConstructorInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "utility.class.with.public.constructor.display.name");
  }


  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "utility.class.with.public.constructor.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiClass psiClass = (PsiClass)infos[0];
    if (psiClass.getConstructors().length > 1) {
      return new UtilityClassWithPublicConstructorFix(true);
    }
    else {
      return new UtilityClassWithPublicConstructorFix(false);
    }
  }

  private static class UtilityClassWithPublicConstructorFix
    extends InspectionGadgetsFix {

    private final boolean m_multipleConstructors;

    UtilityClassWithPublicConstructorFix(boolean multipleConstructors) {
      super();
      m_multipleConstructors = multipleConstructors;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "utility.class.with.public.constructor.make.quickfix",
        Integer.valueOf(m_multipleConstructors ? 1 : 2));
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Make constructors private";
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement classNameIdentifier = descriptor.getPsiElement();
      final PsiClass psiClass = (PsiClass)classNameIdentifier.getParent();
      if (psiClass == null) {
        return;
      }
      final PsiMethod[] constructors = psiClass.getConstructors();
      for (PsiMethod constructor : constructors) {
        final PsiModifierList modifierList =
          constructor.getModifierList();
        modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StaticClassWithPublicConstructorVisitor();
  }

  private static class StaticClassWithPublicConstructorVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      if (!UtilityClassUtil.isUtilityClass(aClass)) {
        return;
      }
      if (!hasPublicConstructor(aClass)) {
        return;
      }
      registerClassError(aClass, aClass);
    }

    private static boolean hasPublicConstructor(PsiClass aClass) {
      final PsiMethod[] constructors = aClass.getConstructors();
      for (final PsiMethod constructor : constructors) {
        if (constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
          return true;
        }
      }
      return false;
    }
  }
}