// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.changeToMethod.transformations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import static java.lang.String.format;
import static org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil.replaceExpression;

final class NotEqualTransformation extends BinaryTransformation {
  @Override
  public void apply(@NotNull GrBinaryExpression expression) {
    GrExpression lhsParenthesized = addParenthesesIfNeeded(getLhs(expression));
    replaceExpression(expression, format("!%s.equals(%s)", lhsParenthesized.getText(), getRhs(expression).getText()));
  }

  @Override
  public String getMethod() {
    return "equals";
  }
}
