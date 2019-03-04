// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import static org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;

/**
 * @author sergey.evdokimov
 * @deprecated please implement {@link org.jetbrains.plugins.groovy.lang.typing.GrCallTypeCalculator}
 */
@SuppressWarnings({"DeprecatedIsStillUsed", "unused"})
@Deprecated
@ScheduledForRemoval(inVersion = "2019.2")
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
