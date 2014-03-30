/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class OverloadedMethodsWithSameNumberOfParametersInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreInconvertibleTypes = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("overloaded.methods.with.same.number.parameters.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("overloaded.methods.with.same.number.parameters.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "overloaded.methods.with.same.number.parameters.option"), this, "ignoreInconvertibleTypes");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OverloadedMethodsWithSameNumberOfParametersVisitor();
  }

  private class OverloadedMethodsWithSameNumberOfParametersVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (method.isConstructor()) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final int parameterCount = parameterList.getParametersCount();
      if (parameterCount == 0) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (MethodUtils.hasSuper(method)) {
        return;
      }
      final String methodName = method.getName();
      final PsiMethod[] sameNameMethods = aClass.findMethodsByName(methodName, false);
      for (PsiMethod sameNameMethod : sameNameMethods) {
        if (method.equals(sameNameMethod)) {
          continue;
        }
        final PsiParameterList otherParameterList = sameNameMethod.getParameterList();
        if (parameterCount == otherParameterList.getParametersCount()) {
          if (ignoreInconvertibleTypes && !areParameterTypesConvertible(parameterList, otherParameterList)) {
            return;
          }
          registerMethodError(method);
          return;
        }
      }
    }

    private boolean areParameterTypesConvertible(PsiParameterList parameterList, PsiParameterList otherParameterList) {
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiParameter[] otherParameters = otherParameterList.getParameters();
      for (int i = 0; i < parameters.length; i++) {
        final PsiType type = parameters[i].getType();
        final PsiType otherType = otherParameters[i].getType();
        if (!type.isAssignableFrom(otherType) && !otherType.isAssignableFrom(type)) {
          return false;
        }
      }
      return true;
    }
  }
}