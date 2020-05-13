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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class StaticSuiteInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "SuiteNotDeclaredStatic";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "static.suite.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StaticSuiteVisitor();
  }

  private static class StaticSuiteVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      //note: no call to super
      @NonNls final String methodName = method.getName();
      if (!"suite".equals(methodName)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (!InheritanceUtil.isInheritor(aClass,
                                       JUnitCommonClassNames.JUNIT_FRAMEWORK_TEST_CASE)) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (!parameterList.isEmpty()) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      registerMethodError(method);
    }
  }
}