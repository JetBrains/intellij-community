// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrCallExpressionTypeCalculator;

/**
 * @author Sergey Evdokimov
 */
public class PredefinedReturnType extends GrCallExpressionTypeCalculator {

  public static final Key<PsiType> PREDEFINED_RETURN_TYPE_KEY = Key.create("PREDEFINED_RETURN_TYPE_KEY");
  
  @Override
  protected PsiType calculateReturnType(@NotNull GrMethodCall callExpression, @NotNull PsiMethod resolvedMethod) {
    return resolvedMethod.getUserData(PREDEFINED_RETURN_TYPE_KEY);
  }
}
