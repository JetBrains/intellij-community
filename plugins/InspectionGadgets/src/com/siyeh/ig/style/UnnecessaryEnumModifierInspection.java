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
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryEnumModifierInspection extends BaseInspection {

  public String getGroupDisplayName() {
    return GroupNames.STYLE_GROUP_NAME;
  }

  public ProblemDescriptor[] doCheckClass(PsiClass aClass,
                                          InspectionManager manager,
                                          boolean isOnTheFly) {
    if (!aClass.isPhysical()) {
      return super.doCheckClass(aClass, manager, isOnTheFly);
    }
    final BaseInspectionVisitor visitor = createVisitor(manager,
                                                        isOnTheFly);
    aClass.accept(visitor);

    return visitor.getErrors();
  }

  public ProblemDescriptor[] doCheckMethod(PsiMethod method,
                                           InspectionManager manager,
                                           boolean isOnTheFly) {
    if (!method.isPhysical()) {
      return super.doCheckMethod(method, manager, isOnTheFly);
    }
    final BaseInspectionVisitor visitor = createVisitor(manager,
                                                        isOnTheFly);
    method.accept(visitor);
    return visitor.getErrors();
  }

  public String buildErrorString(PsiElement location) {
    final PsiModifierList modifierList;
    if (location instanceof PsiModifierList) {
      modifierList = (PsiModifierList)location;
    }
    else {
      modifierList = (PsiModifierList)location.getParent();
    }
    assert modifierList != null;
    final PsiElement parent = modifierList.getParent();
    if (parent instanceof PsiMethod) {
      return InspectionGadgetsBundle.message("unnecessary.enum.modifier.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("unnecessary.enum.modifier.problem.descriptor1");
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryInterfaceModifierVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return new UnnecessaryEnumModifierFix(location);
  }

  private static class UnnecessaryEnumModifierFix
    extends InspectionGadgetsFix {
    private final String m_name;

    private UnnecessaryEnumModifierFix(PsiElement fieldModifiers) {
      super();
      m_name = InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", fieldModifiers.getText());
    }

    public String getName() {
      return m_name;
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {

      final PsiElement element = descriptor.getPsiElement();
      final PsiModifierList modifierList;
      if (element instanceof PsiModifierList) {
        modifierList = (PsiModifierList)element;
      }
      else {
        modifierList = (PsiModifierList)element.getParent();
      }
      assert modifierList != null;
      if (modifierList.getParent() instanceof PsiClass) {
        modifierList.setModifierProperty(PsiModifier.STATIC, false);
      }
      else {
        modifierList.setModifierProperty(PsiModifier.PRIVATE,
                                         false);
      }
    }
  }

  private static class UnnecessaryInterfaceModifierVisitor
    extends BaseInspectionVisitor {
    public void visitClass(@NotNull PsiClass aClass) {
      if (!aClass.isEnum()) {
        return;
      }
      if (!ClassUtils.isInnerClass(aClass)) {
        return;
      }
      if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
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
          registerError(child);
        }
      }
    }

    public void visitMethod(@NotNull PsiMethod method) {
      // don't call super, to keep this from drilling in
      if (!method.isConstructor()) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (!aClass.isEnum()) {
        return;
      }

      final PsiModifierList modifiers = method.getModifierList();
      if (modifiers == null) {
        return;
      }
      final PsiElement[] children = modifiers.getChildren();
      for (final PsiElement child : children) {
        final String text = child.getText();
        if (PsiModifier.PRIVATE.equals(text)) {
          registerError(child);
        }
      }
    }
  }
}
