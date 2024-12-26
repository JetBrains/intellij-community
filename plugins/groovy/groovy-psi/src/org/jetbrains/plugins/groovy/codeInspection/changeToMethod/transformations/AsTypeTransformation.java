// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.changeToMethod.transformations;

import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.util.Objects;

import static java.lang.String.format;
import static org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil.replaceExpression;

public class AsTypeTransformation extends Transformation<GrSafeCastExpression> {
  @Override
  protected @Nullable GrSafeCastExpression checkCast(@NotNull GrExpression expression) {
    return expression instanceof GrSafeCastExpression ? (GrSafeCastExpression) expression : null;
  }

  @Override
  protected boolean couldApply(@NotNull GrSafeCastExpression expression) {
    GrTypeElement typeElement = expression.getCastTypeElement();
    if (typeElement == null ) return false;
    PsiType type = typeElement.getType();
    return type instanceof PsiClassType && ((PsiClassType)type).getParameterCount() == 0;

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
