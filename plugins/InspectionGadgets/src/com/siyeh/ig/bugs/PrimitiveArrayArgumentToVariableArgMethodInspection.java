/*
 * Copyright 2006-2013 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class PrimitiveArrayArgumentToVariableArgMethodInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("primitive.array.argument.to.var.arg.method.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("primitive.array.argument.to.var.arg.method.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PrimitiveArrayArgumentToVariableArgVisitor();
  }

  private static class PrimitiveArrayArgumentToVariableArgVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      if (!PsiUtil.isLanguageLevel5OrHigher(call)) {
        return;
      }
      final PsiExpressionList argumentList = call.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression lastArgument = arguments[arguments.length - 1];
      final PsiType argumentType = lastArgument.getType();
      if (!isPrimitiveArrayType(argumentType)) {
        return;
      }
      final JavaResolveResult result = call.resolveMethodGenerics();
      final PsiMethod method = (PsiMethod)result.getElement();
      if (method == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != arguments.length) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiParameter lastParameter = parameters[parameters.length - 1];
      if (!lastParameter.isVarArgs()) {
        return;
      }
      final PsiType parameterType = lastParameter.getType();
      if (isDeepPrimitiveArrayType(parameterType, result.getSubstitutor())) {
        return;
      }
      registerError(lastArgument);
    }
  }

  private static boolean isPrimitiveArrayType(PsiType type) {
    if (!(type instanceof PsiArrayType)) {
      return false;
    }
    final PsiType componentType = ((PsiArrayType)type).getComponentType();
    return TypeConversionUtil.isPrimitiveAndNotNull(componentType);
  }

  private static boolean isDeepPrimitiveArrayType(PsiType type, PsiSubstitutor substitutor) {
    if (!(type instanceof PsiEllipsisType)) {
      return false;
    }
    final PsiType componentType = type.getDeepComponentType();
    final PsiType substitute = substitutor.substitute(componentType);
    return TypeConversionUtil.isPrimitiveAndNotNull(substitute.getDeepComponentType());
  }
}