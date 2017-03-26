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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.Objects;

import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils.unparenthesize;

abstract class BinaryTransformation extends Transformation<GrBinaryExpression> {

  @NotNull
  protected GrExpression getLhs(@NotNull GrBinaryExpression expression) {
    return unparenthesize(Objects.requireNonNull(expression.getLeftOperand()));
  }

  @NotNull
  protected GrExpression getRhs(@NotNull GrBinaryExpression expression) {
    return unparenthesize(Objects.requireNonNull(expression.getRightOperand()));
  }

  @Nullable
  @Override
  protected GrBinaryExpression checkCast(@NotNull GrExpression expression) {
    return expression instanceof GrBinaryExpression ? (GrBinaryExpression) expression : null;
  }

  @Override
  public boolean couldApply(@NotNull GrBinaryExpression expression) {
    return expression.getRightOperand() != null;
  }
}
