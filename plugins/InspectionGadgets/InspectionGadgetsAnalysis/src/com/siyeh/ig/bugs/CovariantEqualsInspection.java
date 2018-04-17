/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class CovariantEqualsInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "covariant.equals.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "covariant.equals.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CovariantEqualsVisitor();
  }

  private static class CovariantEqualsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final String name = method.getName();
      if (!HardcodedMethodConstants.EQUALS.equals(name)) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 1) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length != 1) return;
      final PsiType argType = parameters[0].getType();
      if (TypeUtils.isJavaLangObject(argType)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final PsiMethod[] methods = aClass.findMethodsByName("equals", false);
      for (PsiMethod method1 : methods) {
        if (MethodUtils.isEquals(method1)) {
          return;
        }
      }
      if (MethodUtils.hasSuper(method)) {
        return;
      }
      registerMethodError(method);
    }
  }
}