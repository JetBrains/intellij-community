/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.ChangeToOperatorInspection.Options;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import java.util.Objects;

/**
 * e.g.
 * a.equals(b)  → (a == b)
 * !a.equals(b) → (a != b)
 */
abstract class BinaryTransformation extends Transformation {

  @NotNull
  protected GrExpression getLhs(@NotNull GrMethodCall methodCall) {
    return Objects.requireNonNull(getBase(methodCall));
  }

  @NotNull
  protected GrExpression getRhs(@NotNull GrMethodCall methodCall) {
    GrExpression[] arguments = methodCall.getExpressionArguments();
    return Objects.requireNonNull(arguments[0]);
  }

  @Override
  public boolean couldApplyInternal(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    GrExpression[] arguments = methodCall.getExpressionArguments();
    return getBase(methodCall) != null && arguments.length == 1;
  }
}
