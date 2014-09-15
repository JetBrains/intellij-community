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
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

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
    if (AnnotationUtil.isAnnotated((PsiMethod)infos[1], IGNORE, false)) {
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

      if (AnnotationUtil.isAnnotated(containingClass, TestUtils.RUN_WITH, true)) {
        return;
      }

      if (AnnotationUtil.isAnnotated(method, IGNORE, false) && method.getName().startsWith("test")) {
        registerMethodError(method, containingClass, method);
      } else if (TestUtils.isJUnit4TestMethod(method)) {
        registerMethodError(method, containingClass, method);
      }
    }
  }
}
