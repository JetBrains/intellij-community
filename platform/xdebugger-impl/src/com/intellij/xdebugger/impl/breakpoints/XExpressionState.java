// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Text;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XExpressionState {
  @Attribute("disabled")
  public boolean myDisabled;

  @Attribute("expression")
  public String myExpression;

  @Attribute("language")
  public String myLanguage;

  @Attribute("custom")
  public String myCustomInfo;

  @Attribute("mode")
  public EvaluationMode myMode = EvaluationMode.EXPRESSION;

  @Text
  public String myOldExpression;

  public XExpressionState() {
  }

  public XExpressionState(boolean disabled, @NotNull String expression, String language, String customInfo, EvaluationMode mode) {
    myDisabled = disabled;
    myExpression = expression;
    myLanguage = language;
    myCustomInfo = customInfo;
    myMode = mode;
  }

  public XExpressionState(boolean disabled, XExpression expression) {
    this(disabled,
         expression.getExpression(),
         expression.getLanguage() != null ? expression.getLanguage().getID() : null,
         expression.getCustomInfo(),
         expression.getMode());
  }

  public XExpressionState(XExpression expression) {
    this(false, expression);
  }

  void checkConverted() {
    if (myOldExpression != null) {
      myExpression = myOldExpression;
      myOldExpression = null;
    }
  }

  @Nullable
  public XExpression toXExpression() {
    checkConverted();
    // old versions may have empty expressions serialized
    if (StringUtil.isEmptyOrSpaces(myExpression)) {
      return null;
    }
    return new XExpressionImpl(myExpression, Language.findLanguageByID(myLanguage), myCustomInfo, myMode);
  }
}
