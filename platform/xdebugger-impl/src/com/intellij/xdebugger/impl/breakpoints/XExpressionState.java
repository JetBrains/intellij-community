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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Text;
import com.intellij.xdebugger.XExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author egor
*/
public class XExpressionState {
  @Attribute("disabled")
  public boolean myDisabled;

  @Attribute("expression")
  public String myExpression;

  @Attribute("language")
  public String myLanguage;

  @Attribute("custom")
  public String myCustomInfo;

  @Text
  public String myOldExpression;

  public XExpressionState() {
  }

  public XExpressionState(boolean disabled, @NotNull String expression, String language, String customInfo) {
    myDisabled = disabled;
    myExpression = expression;
    myLanguage = language;
    myCustomInfo = customInfo;
  }

  public XExpressionState(boolean disabled, XExpression expression) {
    this(disabled, expression.getExpression(), expression.getLanguage() != null ? expression.getLanguage().getID() : null, expression.getCustomInfo());
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
    return new XExpressionImpl(myExpression, Language.findLanguageByID(myLanguage), myCustomInfo);
  }
}
