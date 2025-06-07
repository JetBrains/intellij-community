// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.ChangeToOperatorInspection.Options;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import static org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil.replaceExpression;
import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.BoolUtils.isNegation;
import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils.*;

final class EqualsTransformation extends BinaryTransformation {
  @Override
  public void apply(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    GrExpression rhs = getRhs(methodCall);
    GrExpression rhsParenthesized = checkPrecedenceForBinaryOps(getPrecedence(rhs), GroovyTokenTypes.mEQUAL, true) ? parenthesize(rhs) : rhs;
    GrExpression replacedElement = methodCall;
    String operator = "==";
    if (isNegation(methodCall.getParent())) {
      replacedElement = (GrExpression) methodCall.getParent();
      operator = "!=";
    }

    replaceExpression(replacedElement, getLhs(methodCall).getText() + " " + operator + " " + rhsParenthesized.getText());
  }

  @Override
  protected boolean needParentheses(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    GrExpression rhs = getRhs(methodCall);
    return checkPrecedenceForBinaryOps(getPrecedence(rhs), GroovyTokenTypes.mEQUAL, true) || checkPrecedence(EQUALITY_PRECEDENCE, methodCall);
  }
}
