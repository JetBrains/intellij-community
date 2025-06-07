// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyMethodInfo;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.typing.GrTypeCalculator;

public final class GrDescriptorReturnTypeCalculator implements GrTypeCalculator<GrMethodCall> {

  @Override
  public @Nullable PsiType getType(@NotNull GrMethodCall callExpression) {
    PsiMethod method = callExpression.resolveMethod();
    if (method == null) return null;

    for (GroovyMethodInfo methodInfo : GroovyMethodInfo.getInfos(method)) {
      String returnType = methodInfo.getReturnType();
      if (returnType != null) {
        if (methodInfo.isApplicable(method)) {
          return JavaPsiFacade.getElementFactory(callExpression.getProject()).createTypeFromText(returnType, callExpression);
        }
      }
      else {
        if (methodInfo.isReturnTypeCalculatorDefined()) {
          if (methodInfo.isApplicable(method)) {
            PsiType result = methodInfo.getReturnTypeCalculator().fun(callExpression, method);
            if (result != null) {
              return result;
            }
          }
        }
      }
    }

    return null;
  }
}
