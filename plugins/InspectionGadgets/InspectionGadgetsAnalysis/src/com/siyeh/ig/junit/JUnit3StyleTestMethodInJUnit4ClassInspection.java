/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.psi.*;
import com.intellij.testIntegration.TestFramework;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class JUnit3StyleTestMethodInJUnit4ClassInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("junit3.style.test.method.in.junit4.class.problem.descriptor");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new DelegatingFix(new AddAnnotationPsiFix("org.junit.Test", (PsiMethod)infos[0]));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new JUnit3StyleTestMethodInJUnit4ClassInspectionVisitor();
  }

  private static class JUnit3StyleTestMethodInJUnit4ClassInspectionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final String name = method.getName();
      if (!name.startsWith("test")) return;
      if (!TestUtils.isRunnable(method)) return;
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return;
      final TestFramework testFramework = TestFrameworks.detectFramework(containingClass);
      if (testFramework != null) {
        if (testFramework.isTestMethod(method, false)) {
          @NonNls final String testFrameworkName = testFramework.getName();
          if (testFrameworkName.equals("JUnit4") || testFrameworkName.equals("JUnit5")) return;
        }
        if (AnnotationUtil.isAnnotated(method, "org.junit.Ignore", 0) ||
            testFramework.findSetUpMethod(containingClass) == method ||
            testFramework.findTearDownMethod(containingClass) == method) {
          return;
        }
      }
      if (TestUtils.isJUnitTestClass(containingClass)) return;
      if (!containsJUnit4Annotation(containingClass)) return;
      registerMethodError(method, method);
    }
  }

  public static boolean containsJUnit4Annotation(PsiElement element) {
    final JUnit4AnnotationVisitor visitor = new JUnit4AnnotationVisitor();
    element.accept(visitor);
    return visitor.isJUnit4AnnotationFound();
  }

  private static class JUnit4AnnotationVisitor extends JavaRecursiveElementWalkingVisitor {

    private boolean myJUnit4AnnotationFound = false;

    @Override
    public void visitAnnotation(@NotNull PsiAnnotation annotation) {
      super.visitAnnotation(annotation);
      @NonNls final String qualifiedName = annotation.getQualifiedName();
      if (qualifiedName == null || !qualifiedName.startsWith("org.junit.")) {
        return;
      }
      myJUnit4AnnotationFound = true;
    }

    public boolean isJUnit4AnnotationFound() {
      return myJUnit4AnnotationFound;
    }
  }
}
