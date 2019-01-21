// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParameterListOwner;

/**
 * Represents a Groovy lambda expression.
 */
public interface GrLambdaExpression extends GrExpression, GrParameterListOwner {
  /**
   * Returns PSI element representing lambda expression body: {@link org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock}, {@link GrExpression},
   * or null if the expression is incomplete.
   *
   * @return lambda expression body.
   */
  @Nullable
  PsiElement getBody();
}
