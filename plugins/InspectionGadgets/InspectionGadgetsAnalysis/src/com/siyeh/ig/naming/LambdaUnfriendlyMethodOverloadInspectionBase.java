/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class LambdaUnfriendlyMethodOverloadInspectionBase extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("lambda.unfriendly.method.overload.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiMethod method = (PsiMethod)infos[0];
    return InspectionGadgetsBundle.message(method.isConstructor()
                                           ? "lambda.unfriendly.constructor.overload.problem.descriptor"
                                           : "lambda.unfriendly.method.overload.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LambdaUnfriendlyMethodOverloadVisitor();
  }

  private static class LambdaUnfriendlyMethodOverloadVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      final PsiParameterList parameterList = method.getParameterList();
      final int parametersCount = parameterList.getParametersCount();
      if (parametersCount == 0) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      int functionalIndex = -1;
      for (int i = 0; i < parameters.length; i++) {
        final PsiParameter parameter = parameters[i];
        if (LambdaUtil.isFunctionalType(parameter.getType())) {
          functionalIndex = i;
          break;
        }
      }
       if (functionalIndex < 0) {
         return;
       }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String name = method.getName();
      for (PsiMethod sameNameMethod : containingClass.findMethodsByName(name, true)) {
        if (method.equals(sameNameMethod) || PsiSuperMethodUtil.isSuperMethod(method, sameNameMethod)) {
          continue;
        }
        final PsiParameterList otherParameterList = sameNameMethod.getParameterList();
        if (parametersCount != otherParameterList.getParametersCount()) {
          continue;
        }
        final PsiParameter[] otherParameters = otherParameterList.getParameters();
        final PsiType otherFunctionalType = otherParameters[functionalIndex].getType();
        if (!areOtherParameterTypesConvertible(parameters, otherParameters, functionalIndex) ||
            !LambdaUtil.isFunctionalType(otherFunctionalType)) {
          continue;
        }
        final PsiType functionalType = parameters[functionalIndex].getType();
        if (areSameShapeFunctionalTypes(functionalType, otherFunctionalType)) {
          registerMethodError(method, method);
          return;
        }
      }
    }

    private static boolean areSameShapeFunctionalTypes(PsiType one, PsiType two) {
      final PsiMethod method1 = LambdaUtil.getFunctionalInterfaceMethod(one);
      final PsiMethod method2 = LambdaUtil.getFunctionalInterfaceMethod(two);
      if (method1 == null || method2 == null) {
        return false;
      }
      final PsiType returnType1 = method1.getReturnType();
      final PsiType returnType2 = method2.getReturnType();
      if (PsiType.VOID.equals(returnType1) ^ PsiType.VOID.equals(returnType2)) {
        return false;
      }
      return method1.getParameterList().getParametersCount() == method2.getParameterList().getParametersCount();
    }

    private static boolean areOtherParameterTypesConvertible(PsiParameter[] parameters, PsiParameter[] otherParameters, int notThisOne) {
      for (int i = 0; i < parameters.length; i++) {
        if (i == notThisOne) {
          continue;
        }
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
