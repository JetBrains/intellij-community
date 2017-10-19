/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.testIntegration.TestFramework;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Set;

public class TestCaseWithNoTestMethodsInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreSupers = true;

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "JUnitTestCaseWithNoTests";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "test.case.with.no.test.methods.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "test.case.with.no.test.methods.problem.descriptor");
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message(
        "test.case.with.no.test.methods.option"), this,
      "ignoreSupers");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TestCaseWithNoTestMethodsVisitor();
  }

  private class TestCaseWithNoTestMethodsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface()
          || aClass.isEnum()
          || aClass.isAnnotationType()
          || PsiUtil.isLocalOrAnonymousClass(aClass)
          || aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (aClass instanceof PsiTypeParameter) {
        return;
      }

      Set<TestFramework> applicableFrameworks = TestFrameworks.detectApplicableFrameworks(aClass);
      if (applicableFrameworks.isEmpty()) {
        return;
      }

      if (checkClassHasTestMethods(aClass, applicableFrameworks, true)) {
        return;
      }

      if (ignoreSupers) {
        PsiManager manager = aClass.getManager();
        PsiClass superClass = aClass.getSuperClass();
        while (superClass != null && manager.isInProject(superClass)) {
          if (checkClassHasTestMethods(superClass, applicableFrameworks, false)) {
            return;
          }
          superClass = superClass.getSuperClass();
        }
      }
      registerClassError(aClass);
    }

    private boolean checkClassHasTestMethods(@NotNull PsiClass aClass, Set<TestFramework> applicableFrameworks, boolean checkSuite) {
      PsiMethod[] methods = aClass.getMethods();
      for (TestFramework framework : applicableFrameworks) {
        if (checkSuite && framework instanceof JavaTestFramework && ((JavaTestFramework)framework).isSuiteClass(aClass)) {
          return true;
        }

        if (Arrays.stream(methods).anyMatch(method -> framework.isTestMethod(method, false))) {
          return true;
        }
      }
      return false;
    }
  }
}