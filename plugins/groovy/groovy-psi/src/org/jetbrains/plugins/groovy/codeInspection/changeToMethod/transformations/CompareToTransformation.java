// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.changeToMethod.transformations;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import static java.lang.String.format;
import static org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil.replaceExpression;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mCOMPARE_TO;

final class CompareToTransformation extends BinaryTransformation {

  private final @NotNull IElementType myElementType;

  CompareToTransformation(@NotNull IElementType elementType) {
    myElementType = elementType;
  }

  @Override
  public void apply(@NotNull GrBinaryExpression expression) {
    GrExpression lhsParenthesized = addParenthesesIfNeeded(getLhs(expression));
    String compare = "";
    if (myElementType != mCOMPARE_TO) {
        compare = format(" %s 0", myElementType.toString());
    }

    replaceExpression(expression, format("%s.compareTo(%s) %s", lhsParenthesized.getText(), getRhs(expression).getText(), compare));
  }

  @Override
  public String getMethod() {
    return "compareTo";
  }
}