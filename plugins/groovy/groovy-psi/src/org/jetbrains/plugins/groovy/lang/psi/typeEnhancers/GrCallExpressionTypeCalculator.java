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
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

/**
 * @author sergey.evdokimov
 */
public abstract class GrCallExpressionTypeCalculator {

  public static final ExtensionPointName<GrCallExpressionTypeCalculator> EP_NAME = ExtensionPointName.create("org.intellij.groovy.callExpressionTypeCalculator");

  @Nullable
  protected PsiType calculateReturnType(@NotNull GrMethodCall callExpression, @NotNull PsiMethod resolvedMethod) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  protected PsiType calculateReturnType(@NotNull GrMethodCall callExpression, @Nullable PsiElement resolve) {
    if (resolve instanceof PsiMethod) {
      return calculateReturnType(callExpression, (PsiMethod)resolve);
    }
    return null;
  }

  @Nullable
  public PsiType calculateReturnType(@NotNull GrMethodCall callExpression, GroovyResolveResult[] resolveResults) {
    return calculateReturnType(callExpression, resolveResults.length == 1 ? resolveResults[0].getElement() : null);
  }
}
