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
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.IntArrayList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Bas Leijdekkers
 */
public class LambdaUnfriendlyMethodOverloadInspection extends BaseInspection {

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiMethod method = (PsiMethod)infos[0];
    if (method.isConstructor()) {
      return null;
    }
    return new RenameFix();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

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
      final IntArrayList functionalIndices = new IntArrayList(2);
      for (int i = 0; i < parameters.length; i++) {
        final PsiParameter parameter = parameters[i];
        if (LambdaUtil.isFunctionalType(parameter.getType())) {
          functionalIndices.add(i);
        }
      }
      if (functionalIndices.isEmpty()) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (MethodUtils.hasSuper(method)) {
        return;
      }
      final String name = method.getName();
      outer:
      for (PsiMethod sameNameMethod : containingClass.findMethodsByName(name, true)) {
        if (method.equals(sameNameMethod) || PsiSuperMethodUtil.isSuperMethod(method, sameNameMethod)) {
          continue;
        }
        final PsiParameterList otherParameterList = sameNameMethod.getParameterList();
        if (parametersCount != otherParameterList.getParametersCount()) {
          continue;
        }
        final PsiParameter[] otherParameters = otherParameterList.getParameters();
        if (!areOtherParameterTypesConvertible(parameters, otherParameters, functionalIndices)) {
          continue;
        }
        final int max = functionalIndices.size();
        boolean equalTypes = true;
        for (int i = 0; i < max; i++) {
          final int index = functionalIndices.get(i);
          final PsiType otherFunctionalType = otherParameters[index].getType();
          if (!LambdaUtil.isFunctionalType(otherFunctionalType)) {
            continue outer;
          }
          final PsiType functionalType = parameters[index].getType();
          if (!areSameShapeFunctionalTypes(functionalType, otherFunctionalType)) {
            continue outer;
          }
          equalTypes &= Objects.equals(TypeConversionUtil.erasure(functionalType), TypeConversionUtil.erasure(otherFunctionalType));
        }
        if (equalTypes) {
          continue;
        }

        registerMethodError(method, method);
        return;
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

    private static boolean areOtherParameterTypesConvertible(PsiParameter[] parameters, PsiParameter[] otherParameters,
                                                             IntArrayList ignores) {
      for (int i = 0; i < parameters.length; i++) {
        if (ignores.contains(i)) {
          continue;
        }
        final PsiType type = TypeConversionUtil.erasure(parameters[i].getType());
        final PsiType otherType = TypeConversionUtil.erasure(otherParameters[i].getType());
        if (!type.isAssignableFrom(otherType) && !otherType.isAssignableFrom(type)) {
          return false;
        }
      }
      return true;
    }
  }
}
