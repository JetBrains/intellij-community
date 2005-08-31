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
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class MissingOverrideAnnotationInspection extends MethodInspection {

  private final MissingOverrideAnnotationFix fix = new MissingOverrideAnnotationFix();

  public String getID() {
    return "override";
  }

  public String getGroupDisplayName() {
    return GroupNames.CLASSLAYOUT_GROUP_NAME;
  }

  protected InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class MissingOverrideAnnotationFix
    extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("missing.override.annotation.add.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {

      final PsiElement identifier = descriptor.getPsiElement();
      final PsiModifierListOwner parent =
        (PsiModifierListOwner)identifier.getParent();
      assert parent != null;
      final PsiManager psiManager = parent.getManager();
      final PsiElementFactory factory = psiManager
        .getElementFactory();
      final PsiAnnotation annotation = factory
        .createAnnotationFromText("@java.lang.Override",
                                  parent);
      final PsiModifierList modifierList =
        parent.getModifierList();
      modifierList.addAfter(annotation, null);
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new MissingOverrideAnnotationVisitor();
  }

  private static class MissingOverrideAnnotationVisitor
    extends BaseInspectionVisitor {
    public void visitMethod(@NotNull PsiMethod method) {
      if (method.isConstructor()) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.PRIVATE) ||
          method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiManager manager = method.getManager();
      final LanguageLevel languageLevel =
        manager.getEffectiveLanguageLevel();
      if (languageLevel.equals(LanguageLevel.JDK_1_3) ||
          languageLevel.equals(LanguageLevel.JDK_1_4)) {
        return;
      }
      if (!isOverridden(method)) {
        return;
      }
      if (hasOverrideAnnotation(method)) {
        return;
      }
      registerMethodError(method);
    }
  }

  private static boolean hasOverrideAnnotation(PsiModifierListOwner element) {
    final PsiModifierList modifierList = element.getModifierList();
    if (modifierList == null) {
      return false;
    }
    final PsiAnnotation[] annotations = modifierList.getAnnotations();
    for (final PsiAnnotation annotation : annotations) {
      final PsiJavaCodeReferenceElement reference =
        annotation.getNameReferenceElement();
      if (reference == null) {
        return false;
      }
      final PsiClass annotationClass =
        (PsiClass)reference.resolve();
      if (annotationClass == null) {
        return false;
      }
      final String annotationClassName =
        annotationClass.getQualifiedName();
      if ("java.lang.Override".equals(annotationClassName)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isOverridden(PsiMethod method) {
    final PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      final PsiClass containingClass = superMethod.getContainingClass();
      if (containingClass == null || !containingClass.isInterface()) {
        return true;
      }
    }
    return false;
  }
}