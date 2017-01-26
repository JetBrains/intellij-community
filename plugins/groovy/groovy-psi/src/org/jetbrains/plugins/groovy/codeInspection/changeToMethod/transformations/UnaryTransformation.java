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
import org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;


public class UnaryTransformation extends Transformation<GrUnaryExpression> {

  @NotNull
  private final String myMethod;

  public UnaryTransformation(@NotNull String method) {
    myMethod = method;
  }

  @Override
  public void apply(@NotNull GrUnaryExpression expression) {
    GrExpression operand = addParenthesesIfNeeded(requireNonNull(getOperand(expression)));
    GrInspectionUtil.replaceExpression(expression, format("%s.%s()", operand.getText(), myMethod));
  }

  @NotNull
  @Override
  public String getMethod() {
    return myMethod;
  }

  @Nullable
  @Override
  protected GrUnaryExpression checkCast(@NotNull GrExpression expression) {
    return expression instanceof GrUnaryExpression ? (GrUnaryExpression) expression : null;
  }

  @Override
  public boolean couldApply(@NotNull GrUnaryExpression expression) {
    GrExpression operand = getOperand(expression);
    return operand != null;
  }

  @Nullable
  public static GrExpression getOperand(@NotNull GrUnaryExpression callExpression) {
    return callExpression.getOperand();
  }
}