// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.ChangeToOperatorInspection.Options;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import static java.lang.String.format;
import static org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil.replaceExpression;
import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils.*;

final class IsCaseTransformation extends BinaryTransformation {
  @Override
  public void apply(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    GrExpression rhs = getRhs(methodCall);
    GrExpression rhsParenthesized = checkPrecedenceForNonBinaryOps(rhs, RELATIONAL_PRECEDENCE) ? parenthesize(rhs) : rhs;
    replaceExpression(methodCall, format("%s in %s", rhsParenthesized.getText(), getLhs(methodCall).getText()));
  }

  @Override
  protected boolean needParentheses(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    GrExpression rhs = getRhs(methodCall);
    return checkPrecedenceForNonBinaryOps(rhs, RELATIONAL_PRECEDENCE) || checkPrecedence(RELATIONAL_PRECEDENCE, methodCall);
  }
}
