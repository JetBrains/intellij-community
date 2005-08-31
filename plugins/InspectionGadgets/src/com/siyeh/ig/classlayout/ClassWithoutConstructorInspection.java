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
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class ClassWithoutConstructorInspection extends ClassInspection {

  private final ClassWithoutConstructorFix fix = new ClassWithoutConstructorFix();

  public String getGroupDisplayName() {
    return GroupNames.JAVABEANS_GROUP_NAME;
  }

  protected InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class ClassWithoutConstructorFix
    extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("class.without.constructor.create.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement classIdentifier = descriptor.getPsiElement();
      final PsiClass psiClass = (PsiClass)classIdentifier.getParent();
      final PsiManager psiManager = PsiManager.getInstance(project);
      final PsiElementFactory factory = psiManager.getElementFactory();
      final PsiMethod constructor = factory.createConstructor();
      final PsiModifierList modifierList = constructor.getModifierList();
      assert psiClass != null;
      if (psiClass.hasModifierProperty(PsiModifier.PRIVATE)) {
        modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
        modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
      }
      else if (psiClass.hasModifierProperty(PsiModifier.PROTECTED)) {
        modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
        modifierList.setModifierProperty(PsiModifier.PROTECTED, true);
      }
      else if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        modifierList
          .setModifierProperty(PsiModifier.PUBLIC, false);
        modifierList
          .setModifierProperty(PsiModifier.PROTECTED, true);
      }
      else if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
      }

      psiClass.add(constructor);
      final CodeStyleManager styleManager = psiManager
        .getCodeStyleManager();
      styleManager.reformat(constructor);
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ClassWithoutConstructorVisitor();
  }

  private static class ClassWithoutConstructorVisitor
    extends BaseInspectionVisitor {
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so it doesn't drill down
      if (aClass.isInterface() || aClass.isEnum() ||
          aClass.isAnnotationType()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter ||
          aClass instanceof PsiAnonymousClass) {
        return;
      }
      if (classHasConstructor(aClass)) {
        return;
      }
      registerClassError(aClass);
    }

    private static boolean classHasConstructor(PsiClass aClass) {
      final PsiMethod[] methods = aClass.getMethods();
      for (final PsiMethod method : methods) {
        if (method.isConstructor()) {
          return true;
        }
      }
      return false;
    }
  }
}
