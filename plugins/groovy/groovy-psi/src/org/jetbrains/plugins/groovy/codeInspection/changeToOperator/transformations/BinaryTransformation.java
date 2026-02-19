// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.ChangeToOperatorInspection.Options;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import java.util.Objects;

/**
 * e.g.
 * a.equals(b)  -> (a == b)
 * !a.equals(b) -> (a != b)
 */
@ApiStatus.Internal
public abstract class BinaryTransformation extends Transformation {

  protected @NotNull GrExpression getLhs(@NotNull GrMethodCall methodCall) {
    return Objects.requireNonNull(getBase(methodCall));
  }

  protected @NotNull GrExpression getRhs(@NotNull GrMethodCall methodCall) {
    return getArgument(methodCall, 0);
  }

  @Override
  public boolean couldApplyInternal(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    return getBase(methodCall) != null && checkArgumentsCount(methodCall, 1);
  }
}
