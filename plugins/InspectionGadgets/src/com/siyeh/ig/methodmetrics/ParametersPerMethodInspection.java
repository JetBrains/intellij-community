/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.methodmetrics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class ParametersPerMethodInspection extends MethodMetricInspection {

  public String getID() {
    return "MethodWithTooManyParameters";
  }

  public String getGroupDisplayName() {
    return GroupNames.METHODMETRICS_GROUP_NAME;
  }

  public String buildErrorString(PsiElement location) {
    final PsiMethod method = (PsiMethod)location.getParent();
    assert method != null;
    final PsiParameterList parameterList = method.getParameterList();
    final int numParams = parameterList.getParameters().length;
    return InspectionGadgetsBundle.message("parameters.per.method.problem.descriptor", numParams);
  }

  protected int getDefaultLimit() {
    return 5;
  }

  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message("parameter.limit.option");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ParametersPerMethodVisitor();
  }

  private class ParametersPerMethodVisitor extends BaseInspectionVisitor {

    public void visitMethod(@NotNull PsiMethod method) {
      // note: no call to super
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList == null) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters == null) {
        return;
      }
      if (parameters.length <= getLimit()) {
        return;
      }

      final PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(method);
      for (final PsiMethod superMethod : superMethods) {
        final PsiClass containingClass = superMethod.getContainingClass();
        if (containingClass != null) {
          if (LibraryUtil.classIsInLibrary(containingClass)) {
            return;
          }
        }
      }
      registerMethodError(method);
    }
  }
}
