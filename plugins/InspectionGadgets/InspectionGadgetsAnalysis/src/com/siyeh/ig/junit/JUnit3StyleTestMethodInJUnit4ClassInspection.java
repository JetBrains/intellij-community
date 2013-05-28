/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.siyeh.ig.junit;

import com.intellij.codeInspection.AnnotateMethodFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class JUnit3StyleTestMethodInJUnit4ClassInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("junit3.style.test.method.in.junit4.class.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("junit3.style.test.method.in.junit4.class.problem.descriptor");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new DelegatingFix(new AnnotateMethodFix("org.junit.Test"){
      @Override
      public int shouldAnnotateBaseMethod(PsiMethod method, PsiMethod superMethod, Project project) {
        return 1;
      }
    });
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new JUnit3StyleTestMethodInJUnit4ClassInspectionVisitor();
  }

  private static class JUnit3StyleTestMethodInJUnit4ClassInspectionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      final String name = method.getName();
      if (!name.startsWith("test")) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.ABSTRACT) || !method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      if (TestUtils.isJUnit4TestMethod(method)) {
        return;
      }
      final PsiType returnType = method.getReturnType();
      if (returnType == null || !returnType.equals(PsiType.VOID)) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 0) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (TestUtils.isJUnitTestClass(containingClass)) {
        return;
      }
      if (!containsReferenceToClass(containingClass, "org.junit.Test")) {
        return;
      }
      registerMethodError(method);
    }
  }

  public static boolean containsReferenceToClass(PsiElement element, String fullyQualifiedName) {
    final ClassReferenceVisitor visitor = new ClassReferenceVisitor(fullyQualifiedName);
    element.accept(visitor);
    return visitor.isReferenceFound();
  }

  private static class ClassReferenceVisitor extends JavaRecursiveElementVisitor {

    private final String fullyQualifiedName;
    private boolean referenceFound = false;

    private ClassReferenceVisitor(String fullyQualifiedName) {
      this.fullyQualifiedName = fullyQualifiedName;
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      if (referenceFound) {
        return;
      }
      if (!(reference.getParent() instanceof PsiAnnotation)) {
        // optimization
        return;
      }
      final PsiElement element = reference.resolve();
      if (!(element instanceof PsiClass) || element instanceof PsiTypeParameter) {
        return;
      }
      final PsiClass aClass = (PsiClass)element;
      final String classQualifiedName = aClass.getQualifiedName();
      if (classQualifiedName == null || !classQualifiedName.equals(fullyQualifiedName)) {
        return;
      }
      referenceFound = true;
    }

    public boolean isReferenceFound() {
      return referenceFound;
    }
  }
}
