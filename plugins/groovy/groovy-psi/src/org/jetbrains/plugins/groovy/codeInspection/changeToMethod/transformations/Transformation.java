// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.changeToMethod.transformations;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils.*;

public abstract class Transformation<T extends GrExpression> {

  public boolean couldApplyRow(@NotNull GrExpression expression) {
    T casted = checkCast(expression);
    return casted != null && couldApply(casted);
  }

  public void applyRow(@NotNull GrExpression expression) {
    T casted = checkCast(expression);
    if (casted != null) apply(casted);
  }

  @Nullable
  protected abstract T checkCast(@NotNull GrExpression expression);

  protected abstract boolean couldApply(@NotNull T expression);

  protected abstract void apply(@NotNull T expression);

  public abstract @NlsSafe String getMethod();

  @NotNull
  GrExpression addParenthesesIfNeeded(@NotNull GrExpression expression) {
    return checkPrecedenceForNonBinaryOps(expression, METHOD_CALL_PRECEDENCE) ? parenthesize(expression) : expression;
  }
}
