/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.changeToMethod.transformations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils.METHOD_CALL_PRECEDENCE;
import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils.checkPrecedenceForNonBinaryOps;
import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils.parenthesize;

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

  public abstract String getMethod();

  @NotNull
  GrExpression addParenthesesIfNeeded(@NotNull GrExpression expression) {
    return checkPrecedenceForNonBinaryOps(expression, METHOD_CALL_PRECEDENCE) ? parenthesize(expression) : expression;
  }
}
