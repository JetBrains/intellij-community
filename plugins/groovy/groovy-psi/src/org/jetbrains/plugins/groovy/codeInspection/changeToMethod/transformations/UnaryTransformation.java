// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.changeToMethod.transformations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;


public class UnaryTransformation extends Transformation<GrUnaryExpression> {

  private final @NotNull String myMethod;

  public UnaryTransformation(@NotNull String method) {
    myMethod = method;
  }

  @Override
  public void apply(@NotNull GrUnaryExpression expression) {
    GrExpression operand = addParenthesesIfNeeded(requireNonNull(getOperand(expression)));
    GrInspectionUtil.replaceExpression(expression, format("%s.%s()", operand.getText(), myMethod));
  }

  @Override
  public @NotNull String getMethod() {
    return myMethod;
  }

  @Override
  protected @Nullable GrUnaryExpression checkCast(@NotNull GrExpression expression) {
    return expression instanceof GrUnaryExpression ? (GrUnaryExpression) expression : null;
  }

  @Override
  public boolean couldApply(@NotNull GrUnaryExpression expression) {
    GrExpression operand = getOperand(expression);
    return operand != null;
  }

  public static @Nullable GrExpression getOperand(@NotNull GrUnaryExpression callExpression) {
    return callExpression.getOperand();
  }
}