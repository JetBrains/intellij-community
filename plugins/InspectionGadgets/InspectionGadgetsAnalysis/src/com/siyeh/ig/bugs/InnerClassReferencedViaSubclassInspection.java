/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class InnerClassReferencedViaSubclassInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("inner.class.referenced.via.subclass.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiClass declaringClass = (PsiClass)infos[0];
    final PsiClass referencedClass = (PsiClass)infos[1];
    return InspectionGadgetsBundle.message("inner.class.referenced.via.subclass.problem.descriptor",
                                           declaringClass.getQualifiedName(), referencedClass.getQualifiedName());
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new InnerClassReferencedViaSubclassFix();
  }

  private static class InnerClassReferencedViaSubclassFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("inner.class.referenced.via.subclass.quickfix");
    }
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiIdentifier name = (PsiIdentifier)descriptor.getPsiElement();
      final PsiJavaCodeReferenceElement reference = (PsiJavaCodeReferenceElement)name.getParent();
      final PsiClass aClass = (PsiClass)reference.resolve();
      if (aClass == null) {
        return;
      }
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(reference.getProject());
      final PsiJavaCodeReferenceElement newReferenceElement;
      if (reference instanceof PsiReferenceExpression) {
        newReferenceElement = factory.createReferenceExpression(containingClass);
      } else {
        newReferenceElement = factory.createClassReferenceElement(containingClass);
      }
      final PsiElement qualifier = reference.getQualifier();
      if (qualifier == null) {
        return;
      }
      qualifier.replace(newReferenceElement);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InnerClassReferencedViaSubclassVisitor();
  }

  private static class InnerClassReferencedViaSubclassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      final PsiElement qualifier = reference.getQualifier();
      if (!(qualifier instanceof PsiJavaCodeReferenceElement)) {
        return;
      }
      final PsiJavaCodeReferenceElement qualifierReference = (PsiJavaCodeReferenceElement)qualifier;
      final PsiElement qualifierTarget = qualifierReference.resolve();
      if (!(qualifierTarget instanceof PsiClass)) {
        return;
      }
      final PsiClass qualifierClass = (PsiClass)qualifierTarget;
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!qualifierClass.isInheritor(containingClass, true)) {
        return;
      }
      final PsiElement identifier = reference.getReferenceNameElement();
      if (identifier == null) {
        return;
      }
      registerError(identifier, containingClass, qualifierClass);
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }
  }
}
