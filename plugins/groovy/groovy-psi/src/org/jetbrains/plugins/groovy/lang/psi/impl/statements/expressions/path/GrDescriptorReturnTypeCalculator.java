/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.GroovyMethodInfo;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrCallExpressionTypeCalculator;

/**
 * @author Sergey Evdokimov
 */
public class GrDescriptorReturnTypeCalculator extends GrCallExpressionTypeCalculator {

  @Override
  public PsiType calculateReturnType(@NotNull GrMethodCall callExpression, @NotNull PsiMethod method) {
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
