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

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.ChangeToOperatorInspection.Options;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import static java.lang.String.format;
import static org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil.replaceExpression;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ComparisonUtils.isComparison;
import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils.*;

class CompareToTransformation extends BinaryTransformation {
  @Override
  public void apply(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    GrExpression rhs = getRhs(methodCall);
    GrExpression rhsParenthesized = checkPrecedenceForNonBinaryOps(rhs, RELATIONAL_PRECEDENCE) ? parenthesize(rhs) : rhs;
    GrExpression replacedElement = methodCall;
    IElementType changeToOperator = shouldChangeToOperator(methodCall, options);
    if (changeToOperator != mCOMPARE_TO) {
        replacedElement = (GrExpression) methodCall.getParent();
    }

    replaceExpression(replacedElement, format("%s %s %s", getLhs(methodCall).getText(), changeToOperator, rhsParenthesized.getText()));
  }

  @Nullable
  private static IElementType shouldChangeToOperator(@NotNull GrMethodCall call, Options options) {
    PsiElement parent = call.getParent();
    if (isComparison(parent)) {
      IElementType token = ((GrBinaryExpression)parent).getOperationTokenType();
      if (isEquality(token) && !options.shouldChangeCompareToEqualityToEquals()) {
        return null;
      }
      return token;
    }
    return mCOMPARE_TO;

  }

  private static boolean isEquality(IElementType comparison) {
    return (comparison == mNOT_EQUAL) || (comparison == mEQUAL);
  }

  @Override
  public boolean couldApplyInternal(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    return super.couldApplyInternal(methodCall, options) && shouldChangeToOperator(methodCall, options) != null;
  }

  @Override
  protected boolean needParentheses(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    GrExpression rhs = getRhs(methodCall);
    return checkPrecedenceForNonBinaryOps(rhs, RELATIONAL_PRECEDENCE) || checkPrecedence(RELATIONAL_PRECEDENCE, methodCall);
  }
}