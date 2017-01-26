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

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.ChangeToOperatorInspection.Options;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils;

import static java.util.Objects.requireNonNull;


public class UnaryTransformation extends Transformation {

  private final IElementType myOperator;

  public UnaryTransformation(@NotNull IElementType operatorType) {
    myOperator = operatorType;
  }

  @Override
  public void apply(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    GrInspectionUtil.replaceExpression(methodCall, myOperator.toString() + requireNonNull(getBase(methodCall)).getText());
  }

  @Override
  public boolean couldApplyInternal(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    return getBase(methodCall)!= null && methodCall.getExpressionArguments().length == 0;
  }

  @Override
  protected boolean needParentheses(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    return ParenthesesUtils.checkPrecedence(ParenthesesUtils.PREFIX_PRECEDENCE, methodCall);
  }
}