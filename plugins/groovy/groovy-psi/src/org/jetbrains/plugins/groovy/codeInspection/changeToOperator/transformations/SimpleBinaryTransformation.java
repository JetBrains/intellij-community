// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.ChangeToOperatorInspection.Options;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import static java.lang.String.format;
import static org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil.replaceExpression;
import static org.jetbrains.plugins.groovy.lang.psi.impl.utils.ParenthesesUtils.*;

public class SimpleBinaryTransformation extends BinaryTransformation {

  private final IElementType myOperator;

  public SimpleBinaryTransformation(@NotNull IElementType operatorType) {
    myOperator = operatorType;
  }

  @NotNull
  protected String getOperatorText() {
    return myOperator.toString();
  }

  @Override
  public void apply(@NotNull GrMethodCall methodCall, @NotNull Options options) {
    GrExpression rhs = getRhs(methodCall);

    rhs = checkPrecedenceForBinaryOps(getPrecedence(rhs), myOperator, true) ? parenthesize(rhs) : rhs;
    replaceExpression(methodCall, format("%s %s %s", getLhs(methodCall).getText(), getOperatorText(), rhs.getText()));
  }

  protected boolean needParentheses(@NotNull GrMethodCall methodCall,
                                    @NotNull Options options) {
    GrExpression rhs = getRhs(methodCall);
    int rhsPrecedence = getPrecedence(rhs);
    return checkPrecedenceForBinaryOps(rhsPrecedence, myOperator, true) ||
           checkPrecedence(precedenceForBinaryOperator(myOperator), methodCall);
  }
}
