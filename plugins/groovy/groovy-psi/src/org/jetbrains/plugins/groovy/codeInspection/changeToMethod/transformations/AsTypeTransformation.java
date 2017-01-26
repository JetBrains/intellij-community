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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.util.Objects;

import static java.lang.String.format;
import static org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil.replaceExpression;

public class AsTypeTransformation extends Transformation<GrSafeCastExpression> {
  @Nullable
  @Override
  protected GrSafeCastExpression checkCast(@NotNull GrExpression expression) {
    return expression instanceof GrSafeCastExpression ? (GrSafeCastExpression) expression : null;
  }

  @Override
  protected boolean couldApply(@NotNull GrSafeCastExpression expression) {
    return expression.getCastTypeElement() != null;
  }

  @Override
  protected void apply(@NotNull GrSafeCastExpression expression) {
    GrExpression lhsParenthesized = addParenthesesIfNeeded(expression.getOperand());
    GrTypeElement typeElement = Objects.requireNonNull(expression.getCastTypeElement());
    replaceExpression(expression, format("%s.asType(%s)", lhsParenthesized.getText(), typeElement.getText()));
  }

  @Override
  public String getMethod() {
    return "asType";
  }
}
