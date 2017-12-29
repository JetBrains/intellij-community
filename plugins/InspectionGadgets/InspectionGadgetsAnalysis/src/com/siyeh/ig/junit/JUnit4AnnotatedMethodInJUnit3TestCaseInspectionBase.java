// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_HIERARCHY;

public class JUnit4AnnotatedMethodInJUnit3TestCaseInspectionBase extends BaseInspection {
  protected static final String IGNORE = "org.junit.Ignore";

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("junit4.test.method.in.class.extending.junit3.testcase.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    if (AnnotationUtil.isAnnotated((PsiMethod)infos[1], IGNORE, 0)) {
      return InspectionGadgetsBundle.message("ignore.test.method.in.class.extending.junit3.testcase.problem.descriptor");
    }
    return InspectionGadgetsBundle.message("junit4.test.method.in.class.extending.junit3.testcase.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Junit4AnnotatedMethodInJunit3TestCaseVisitor();
  }

  private static class Junit4AnnotatedMethodInJunit3TestCaseVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!TestUtils.isJUnitTestClass(containingClass)) {
        return;
      }

      if (AnnotationUtil.isAnnotated(containingClass, TestUtils.RUN_WITH, CHECK_HIERARCHY)) {
        return;
      }

      if (AnnotationUtil.isAnnotated(method, IGNORE, 0) && method.getName().startsWith("test") ||
          TestUtils.isJUnit4TestMethod(method)) {
        registerMethodError(method, containingClass, method);
      }
    }
  }
}
