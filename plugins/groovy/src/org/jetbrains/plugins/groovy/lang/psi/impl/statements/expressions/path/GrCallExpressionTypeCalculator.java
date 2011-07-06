/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * @author sergey.evdokimov
 */
public abstract class GrCallExpressionTypeCalculator {

  public static final ExtensionPointName<GrCallExpressionTypeCalculator> EP_NAME = ExtensionPointName.create("org.intellij.groovy.callExpressionTypeCalculator");

  @Nullable
  public abstract PsiType calculateReturnType(@NotNull GrMethodCall callExpression);

  @Nullable
  protected static PsiMethod resolveMethodCall(@NotNull GrMethodCall callExpression) {
    GrExpression eInvokedExpression = callExpression.getInvokedExpression();
    if (!(eInvokedExpression instanceof GrReferenceExpression)) return null;
    return resolveMethodCall((GrReferenceExpression)eInvokedExpression);
  }

  @Nullable
  protected static PsiMethod resolveMethodCall(@NotNull GrReferenceExpression invokedExpression) {
    GroovyResolveResult[] resolveResults = invokedExpression.multiResolve(false);
    if (resolveResults.length == 0) {
      return null;
    }

    PsiElement eMethod = resolveResults[0].getElement();
    if (!(eMethod instanceof PsiMethod)) return null;

    return (PsiMethod)eMethod;
  }
}
