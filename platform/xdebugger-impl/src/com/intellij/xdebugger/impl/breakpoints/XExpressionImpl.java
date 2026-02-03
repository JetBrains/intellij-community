// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.lang.Language;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class XExpressionImpl implements XExpression {
  public static final XExpression EMPTY_EXPRESSION = fromText("", EvaluationMode.EXPRESSION);
  public static final XExpression EMPTY_CODE_FRAGMENT = fromText("", EvaluationMode.CODE_FRAGMENT);

  private final @NotNull String myExpression;
  private final Language myLanguage;
  private final String myCustomInfo;
  private final EvaluationMode myMode;

  public XExpressionImpl(@NotNull String expression, Language language, String customInfo) {
    this(expression, language, customInfo, EvaluationMode.EXPRESSION);
  }

  public XExpressionImpl(@NotNull String expression, Language language, String customInfo, @NotNull EvaluationMode mode) {
    myExpression = expression;
    myLanguage = language;
    myCustomInfo = customInfo;
    myMode = mode;
  }

  @Override
  public @NotNull String getExpression() {
    return myExpression;
  }

  @Override
  public Language getLanguage() {
    return myLanguage;
  }

  @Override
  public String getCustomInfo() {
    return myCustomInfo;
  }

  @Override
  public @NotNull EvaluationMode getMode() {
    return myMode;
  }

  @Contract("null -> null; !null -> !null")
  public static XExpressionImpl fromText(@Nullable String text) {
    return text != null ? new XExpressionImpl(text, null, null, EvaluationMode.EXPRESSION) : null;
  }

  @Contract("null, _ -> null; !null, _ -> !null")
  public static XExpressionImpl fromText(@Nullable String text, @NotNull EvaluationMode mode) {
    return text != null ? new XExpressionImpl(text, null, null, mode) : null;
  }

  public static XExpressionImpl changeMode(@NotNull XExpression expression, @NotNull EvaluationMode mode) {
    return new XExpressionImpl(expression.getExpression(), expression.getLanguage(), expression.getCustomInfo(), mode);
  }

  public static XExpressionImpl changeLanguage(@NotNull XExpression expression, @Nullable Language language) {
    return new XExpressionImpl(expression.getExpression(), language, expression.getCustomInfo(), expression.getMode());
  }

  @Override
  public String toString() {
    return myExpression;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    XExpressionImpl that = (XExpressionImpl)o;

    if (!Objects.equals(myCustomInfo, that.myCustomInfo)) return false;
    if (!myExpression.equals(that.myExpression)) return false;
    if (!Objects.equals(myLanguage, that.myLanguage)) return false;
    if (myMode != that.myMode) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myExpression, myLanguage, myCustomInfo, myMode);
  }
}
