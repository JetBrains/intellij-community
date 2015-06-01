/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;

public class TestCaseWithConstructorInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "JUnitTestCaseWithNonTrivialConstructors";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "test.case.with.constructor.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    if (Boolean.TRUE.equals(infos[0])) {
      return InspectionGadgetsBundle.message(
        "test.case.with.constructor.problem.descriptor.initializer");
    }
    else {
      return InspectionGadgetsBundle.message(
        "test.case.with.constructor.problem.descriptor");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TestCaseWithConstructorVisitor();
  }

  private static class TestCaseWithConstructorVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!method.isConstructor()) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (!TestUtils.isJUnitTestClass(aClass) && !TestUtils.isJUnit4TestClass(aClass, false)) {
        return;
      }
      if (MethodUtils.isTrivial(method, false)) {
        return;
      }
      registerMethodError(method, Boolean.FALSE);
    }

    @Override
    public void visitClassInitializer(PsiClassInitializer initializer) {
      if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiClass aClass = initializer.getContainingClass();
      if (!TestUtils.isJUnitTestClass(aClass) && !TestUtils.isJUnit4TestClass(aClass, true)) {
        return;
      }
      if (MethodUtils.isTrivial(initializer)) {
        return;
      }
      registerClassInitializerError(initializer, Boolean.TRUE);
    }
  }
}