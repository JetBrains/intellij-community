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

/**
* @author egor
*/
public class XExpressionImpl implements XExpression {
  public final String myExpression;
  public final Language myLanguage;
  public final String myCustomInfo;

  public XExpressionImpl(String expression, Language language, String customInfo) {
    myExpression = expression;
    myLanguage = language;
    myCustomInfo = customInfo;
  }

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

  public static XExpressionImpl fromText(String text) {
    return text != null ? new XExpressionImpl(text, null, null) : null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    XExpressionImpl that = (XExpressionImpl)o;

    if (myCustomInfo != null ? !myCustomInfo.equals(that.myCustomInfo) : that.myCustomInfo != null) return false;
    if (myExpression != null ? !myExpression.equals(that.myExpression) : that.myExpression != null) return false;
    if (myLanguage != null ? !myLanguage.equals(that.myLanguage) : that.myLanguage != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myExpression != null ? myExpression.hashCode() : 0;
    result = 31 * result + (myLanguage != null ? myLanguage.hashCode() : 0);
    result = 31 * result + (myCustomInfo != null ? myCustomInfo.hashCode() : 0);
    return result;
  }
}
