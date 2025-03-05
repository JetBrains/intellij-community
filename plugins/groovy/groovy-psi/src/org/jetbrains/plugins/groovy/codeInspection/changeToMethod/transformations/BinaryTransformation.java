// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.changeToMethod.transformations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.Objects;

import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils.unparenthesize;

abstract class BinaryTransformation extends Transformation<GrBinaryExpression> {

  protected @NotNull GrExpression getLhs(@NotNull GrBinaryExpression expression) {
    return unparenthesize(Objects.requireNonNull(expression.getLeftOperand()));
  }

  protected @NotNull GrExpression getRhs(@NotNull GrBinaryExpression expression) {
    return unparenthesize(Objects.requireNonNull(expression.getRightOperand()));
  }

  @Override
  protected @Nullable GrBinaryExpression checkCast(@NotNull GrExpression expression) {
    return expression instanceof GrBinaryExpression ? (GrBinaryExpression) expression : null;
  }

  @Override
  public boolean couldApply(@NotNull GrBinaryExpression expression) {
    return expression.getRightOperand() != null;
  }
}
