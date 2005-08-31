/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class UtilityClassWithoutPrivateConstructorInspection extends ClassInspection {

  public String getGroupDisplayName() {
    return GroupNames.CLASSLAYOUT_GROUP_NAME;
  }

  protected InspectionGadgetsFix buildFix(PsiElement location) {
    final PsiClass aClass = (PsiClass)location.getParent();
    if (hasNullArgConstructor(aClass)) {
      return new MakeConstructorPrivateFix();
    }
    else {
      return new CreateEmptyPrivateConstructor();
    }
  }

  private static class CreateEmptyPrivateConstructor
    extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("utility.class.without.private.constructor.create.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {

      final PsiElement classNameIdentifier = descriptor.getPsiElement();
      final PsiClass aClass = (PsiClass)classNameIdentifier
        .getParent();
      assert aClass != null;
      if (hasNullArgConstructor(aClass)) {

        final PsiMethod[] methods = aClass.getMethods();
        for (final PsiMethod method : methods) {
          if (method.isConstructor()) {
            final PsiParameterList params = method
              .getParameterList();
            if (params != null
                && params.getParameters().length == 0) {
              final PsiModifierList modifiers = aClass
                .getModifierList();
              modifiers
                .setModifierProperty(PsiModifier.PUBLIC,
                                     true);
              modifiers
                .setModifierProperty(PsiModifier.PROTECTED,
                                     true);
              modifiers
                .setModifierProperty(PsiModifier.PRIVATE,
                                     true);
            }
          }
        }
      }
      else {
        final PsiManager psiManager = PsiManager.getInstance(project);
        final PsiElementFactory factory = psiManager
          .getElementFactory();
        final PsiMethod constructor = factory.createConstructor();
        final PsiModifierList modifierList = constructor
          .getModifierList();
        modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
        aClass.add(constructor);
        final CodeStyleManager styleManager = psiManager
          .getCodeStyleManager();
        styleManager.reformat(constructor);
      }
    }
  }

  private static class MakeConstructorPrivateFix
    extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("utility.class.without.private.constructor.make.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {

      final PsiElement classNameIdentifier = descriptor.getPsiElement();
      final PsiClass aClass = (PsiClass)classNameIdentifier
        .getParent();
      assert aClass != null;

      final PsiMethod[] methods = aClass.getMethods();
      for (final PsiMethod method : methods) {
        if (method.isConstructor()) {
          final PsiParameterList params = method
            .getParameterList();
          if (params != null
              && params.getParameters().length == 0) {
            final PsiModifierList modifiers =
              method.getModifierList();
            modifiers.setModifierProperty(PsiModifier.PUBLIC,
                                          true);
            modifiers.setModifierProperty(PsiModifier.PROTECTED,
                                          true);
            modifiers.setModifierProperty(PsiModifier.PRIVATE,
                                          true);
          }
        }
      }
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new StaticClassWithoutPrivateConstructorVisitor();
  }

  private static class StaticClassWithoutPrivateConstructorVisitor
    extends BaseInspectionVisitor {
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      if (!UtilityClassUtil.isUtilityClass(aClass)) {
        return;
      }

      if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (hasPrivateConstructor(aClass)) {
        return;
      }
      registerClassError(aClass);
    }
  }

  private static boolean hasPrivateConstructor(PsiClass aClass) {
    final PsiMethod[] methods = aClass.getMethods();
    for (final PsiMethod method : methods) {
      if (method.isConstructor() && method
        .hasModifierProperty(PsiModifier.PRIVATE)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasNullArgConstructor(PsiClass aClass) {
    final PsiMethod[] methods = aClass.getMethods();
    for (final PsiMethod method : methods) {
      if (method.isConstructor()) {
        final PsiParameterList params = method.getParameterList();
        if (params != null && params.getParameters().length == 0) {
          return true;
        }
      }
    }
    return false;
  }
}
