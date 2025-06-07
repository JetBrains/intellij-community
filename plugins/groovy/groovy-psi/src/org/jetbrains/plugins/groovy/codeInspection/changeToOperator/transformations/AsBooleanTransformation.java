// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.ChangeToOperatorInspection.Options;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;

import static java.util.Objects.requireNonNull;
import static org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil.replaceExpression;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mLNOT;
import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils.PREFIX_PRECEDENCE;
import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils.checkPrecedence;

/**
 * e.g.
 * !a.asBoolean()    -> !a
 * a.asBoolean()     -> !!a
 * if(a.asBoolean()) -> if(a)
 */
class AsBooleanTransformation extends Transformation {
  public static final String NEGATION = mLNOT.toString();
  public static final String DOUBLE_NEGATION = NEGATION + NEGATION;

  protected @Nullable String getPrefix(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    if (isImplicitlyBoolean(methodCall)) {
      return "";
    }
    else if (options.useDoubleNegation()) {
      return DOUBLE_NEGATION;
    }
    else {
      return null;
    }
  }

  @Override
  public void apply(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    replaceExpression(methodCall, getPrefix(methodCall, options) + requireNonNull(getBase(methodCall)).getText());
  }

  @Override
  public boolean couldApplyInternal(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    return getBase(methodCall) != null
           && methodCall.getExpressionArguments().length == 0
           && getPrefix(methodCall, options) != null
           && methodCall.getClosureArguments().length == 0;
  }

  @Override
  protected boolean needParentheses(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    String prefix = getPrefix(methodCall, options);
    return DOUBLE_NEGATION.equals(prefix) && checkPrecedence(PREFIX_PRECEDENCE, methodCall);
  }

  private static boolean isImplicitlyBoolean(GrMethodCall methodCall) {
    PsiElement parent = methodCall.getParent();
    if (parent instanceof GrIfStatement || parent instanceof GrWhileStatement) return true;
    if (parent instanceof GrConditionalExpression && ((GrConditionalExpression)parent).getCondition().equals(methodCall)) return true;
    if (parent instanceof GrUnaryExpression && PsiTypes.booleanType().equals(((GrUnaryExpression)parent).getType())) return true;
    if (parent instanceof GrBinaryExpression && PsiTypes.booleanType().equals(((GrBinaryExpression)parent).getType())) return true;
    return false;
  }
}
