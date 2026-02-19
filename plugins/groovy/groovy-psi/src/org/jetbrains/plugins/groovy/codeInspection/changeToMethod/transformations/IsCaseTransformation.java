// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.changeToMethod.transformations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import static java.lang.String.format;
import static org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil.replaceExpression;
import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils.unparenthesize;

final class IsCaseTransformation extends BinaryTransformation {
  @Override
  public void apply(@NotNull GrBinaryExpression expression) {
    GrExpression rhsParenthesized = addParenthesesIfNeeded(getRhs(expression));
    replaceExpression(expression, format("%s.isCase(%s)", rhsParenthesized.getText(), unparenthesize(getLhs(expression)).getText()));
  }

  @Override
  public String getMethod() {
    return "isCase";
  }
}
