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

import static com.siyeh.ig.psiutils.ParenthesesUtils.ASSIGNMENT_PRECEDENCE;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil.replaceExpression;
import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils.*;

class PutAtTransformation extends Transformation {
  @Override
  public void apply(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    GrExpression base = requireNonNull(getBase(methodCall));
    GrExpression key = getArgument(methodCall, 0);
    GrExpression rhs = getArgument(methodCall, 1);
    rhs = checkPrecedenceForNonBinaryOps(rhs, ASSIGNMENT_PRECEDENCE) ? parenthesize(rhs) : rhs;
    String result = format("%s[%s] = %s", base.getText(), key.getText(), rhs.getText());
    replaceExpression(methodCall, result);
  }

  @Override
  public boolean couldApplyInternal(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    return getBase(methodCall) != null && checkArgumentsCount(methodCall, 2);
  }

  @Override
  protected boolean needParentheses(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    GrExpression rhs = methodCall.getExpressionArguments()[1];
    return checkPrecedenceForNonBinaryOps(rhs, ASSIGNMENT_PRECEDENCE) || checkPrecedence(ASSIGNMENT_PRECEDENCE, methodCall);
  }
}
