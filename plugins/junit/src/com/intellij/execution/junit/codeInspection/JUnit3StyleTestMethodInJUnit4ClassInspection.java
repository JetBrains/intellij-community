// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.intention.AddAnnotationModCommandAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.execution.JUnitBundle;
import com.intellij.psi.*;
import com.intellij.testIntegration.TestFramework;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class JUnit3StyleTestMethodInJUnit4ClassInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return JUnitBundle.message("junit3.style.test.method.in.junit4.class.problem.descriptor");
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    return LocalQuickFix.from(new AddAnnotationModCommandAction("org.junit.Test", (PsiMethod)infos[0]));
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
          final @NonNls String testFrameworkName = testFramework.getName();
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
      final @NonNls String qualifiedName = annotation.getQualifiedName();
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
