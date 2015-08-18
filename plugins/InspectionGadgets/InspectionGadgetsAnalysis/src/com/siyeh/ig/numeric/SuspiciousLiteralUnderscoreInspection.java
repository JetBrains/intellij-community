/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.numeric;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class SuspiciousLiteralUnderscoreInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("suspicious.literal.underscore.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("suspicious.literal.underscore.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousLiteralUnderscoreVisitor();
  }

  private static class SuspiciousLiteralUnderscoreVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (!PsiType.SHORT.equals(type) && !PsiType.INT.equals(type) && !PsiType.LONG.equals(type) &&
          !PsiType.FLOAT.equals(type) && !PsiType.DOUBLE.equals(type)) {
        return;
      }
      final String text = expression.getText();
      if (text.startsWith("0") && !text.startsWith("0.")) {
        // don't check octal, hexadecimal or binary literals
        return;
      }
      if (!text.contains("_")) {
        return;
      }
      boolean underscore = false;
      boolean group = false;
      boolean dot = false;
      int digit = 0;
      final int index = StringUtil.indexOfAny(text, "fledFLED"); // suffixes and floating point exponent
      final int length = index > 0 ? index : text.length();
      for (int i = 0; i < length; i++) {
        final char c = text.charAt(i);
        if (c == '_' || c == '.') {
          if (underscore) {
            return;
          }
          underscore = true;
          if (digit != 3 && group || digit > 3) {
            registerErrorAtOffset(expression, i - digit, digit);
          }
          group = true;
          digit = 0;
          if (c == '.') {
            dot = true;
          }
        }
        else if (Character.isDigit(c)) {
          underscore = false;
          digit++;
        }
        else {
          return;
        }
      }
      if (dot ? digit > 3 : digit != 3) {
        registerErrorAtOffset(expression, length - digit, digit);
      }
    }
  }
}
