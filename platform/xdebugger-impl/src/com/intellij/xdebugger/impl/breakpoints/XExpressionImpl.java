/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.lang.Language;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author egor
*/
public class XExpressionImpl implements XExpression {
  public static final XExpression EMPTY_EXPRESSION = fromText("", EvaluationMode.EXPRESSION);
  public static final XExpression EMPTY_CODE_FRAGMENT = fromText("", EvaluationMode.CODE_FRAGMENT);

  @NotNull private final String myExpression;
  private final Language myLanguage;
  private final String myCustomInfo;
  private final EvaluationMode myMode;

  public XExpressionImpl(@NotNull String expression, Language language, String customInfo) {
    this(expression, language, customInfo, EvaluationMode.EXPRESSION);
  }

  public XExpressionImpl(@NotNull String expression, Language language, String customInfo, EvaluationMode mode) {
    myExpression = expression;
    myLanguage = language;
    myCustomInfo = customInfo;
    myMode = mode;
  }

  @NotNull
  @Override
  public String getExpression() {
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
  public EvaluationMode getMode() {
    return myMode;
  }

  @Contract("null -> null; !null -> !null")
  public static XExpressionImpl fromText(@Nullable String text) {
    return text != null ? new XExpressionImpl(text, null, null, EvaluationMode.EXPRESSION) : null;
  }

  @Contract("null, _ -> null; !null, _ -> !null")
  public static XExpressionImpl fromText(@Nullable String text, EvaluationMode mode) {
    return text != null ? new XExpressionImpl(text, null, null, mode) : null;
  }

  public static XExpressionImpl changeMode(XExpression expression, EvaluationMode mode) {
    return new XExpressionImpl(expression.getExpression(), expression.getLanguage(), expression.getCustomInfo(), mode);
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

    if (myCustomInfo != null ? !myCustomInfo.equals(that.myCustomInfo) : that.myCustomInfo != null) return false;
    if (!myExpression.equals(that.myExpression)) return false;
    if (myLanguage != null ? !myLanguage.equals(that.myLanguage) : that.myLanguage != null) return false;
    if (myMode != that.myMode) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myExpression.hashCode();
    result = 31 * result + (myLanguage != null ? myLanguage.hashCode() : 0);
    result = 31 * result + (myCustomInfo != null ? myCustomInfo.hashCode() : 0);
    result = 31 * result + (myMode != null ? myMode.hashCode() : 0);
    return result;
  }
}
