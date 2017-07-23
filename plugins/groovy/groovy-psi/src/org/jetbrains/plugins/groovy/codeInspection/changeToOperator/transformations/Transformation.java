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

  @Nullable
  public static GrExpression getBase(@NotNull GrMethodCall callExpression) {
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

  @NotNull
  public GrExpression getArgument(@NotNull GrMethodCall callExpression, int index) {
    GrExpression[] expressionArguments = callExpression.getExpressionArguments();
    if (index < expressionArguments.length) {
      return expressionArguments[index];
    }

    return callExpression.getClosureArguments()[index - expressionArguments.length];
  }
}
