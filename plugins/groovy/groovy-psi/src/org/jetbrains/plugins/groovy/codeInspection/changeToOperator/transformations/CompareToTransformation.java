// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import static org.jetbrains.plugins.groovy.lang.psi.util.LiteralUtilKt.isZero;

final class CompareToTransformation extends BinaryTransformation {
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

  private static @Nullable IElementType shouldChangeToOperator(@NotNull GrMethodCall call, Options options) {
    PsiElement parent = call.getParent();
    if (isComparison(parent) && isZero(((GrBinaryExpression)parent).getRightOperand())) {
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