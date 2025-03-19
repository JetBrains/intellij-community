// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.ChangeToOperatorInspection.Options;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtilKt.isSuperExpression;

public abstract class Transformation {

  public boolean couldApply(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    return couldApplyInternal(methodCall, options) && (!options.withoutAdditionalParentheses() || !needParentheses(methodCall, options));
  }

  protected abstract boolean couldApplyInternal(@NotNull GrMethodCall methodCall, @NotNull Options options);

  protected abstract boolean needParentheses(@NotNull GrMethodCall methodCall, @NotNull Options options);

  public abstract void apply(@NotNull GrMethodCall methodCall, @NotNull Options options);

  public static @Nullable GrExpression getBase(@NotNull GrMethodCall callExpression) {
    GrExpression expression = callExpression.getInvokedExpression();
    GrReferenceExpression invokedExpression = (GrReferenceExpression)expression;
    GrExpression qualifier = invokedExpression.getQualifierExpression();
    if (isSuperExpression(qualifier)) return null;
    return qualifier;
  }

  public boolean checkArgumentsCount(@NotNull GrMethodCall callExpression, int count) {
    if (callExpression.getNamedArguments().length != 0) return false;
    return callExpression.getExpressionArguments().length + callExpression.getClosureArguments().length ==  count;
  }

  public @NotNull GrExpression getArgument(@NotNull GrMethodCall callExpression, int index) {
    GrExpression[] expressionArguments = callExpression.getExpressionArguments();
    if (index < expressionArguments.length) {
      return expressionArguments[index];
    }

    return callExpression.getClosureArguments()[index - expressionArguments.length];
  }
}
