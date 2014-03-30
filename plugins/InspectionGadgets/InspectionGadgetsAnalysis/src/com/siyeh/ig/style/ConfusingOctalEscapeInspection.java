/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.psi.PsiLiteralExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public class ConfusingOctalEscapeInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "ConfusingOctalEscapeSequence";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("confusing.octal.escape.sequence.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("confusing.octal.escape.sequence.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConfusingOctalEscapeVisitor();
  }

  private static class ConfusingOctalEscapeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      if (!ExpressionUtils.hasStringType(expression)) {
        return;
      }
      final String text = expression.getText();
      int escapeStart = -1;
      while (true) {
        escapeStart = text.indexOf((int)'\\', escapeStart + 1);
        if (escapeStart < 0) {
          return;
        }
        if (escapeStart > 0 && text.charAt(escapeStart - 1) == '\\') {
          continue;
        }
        boolean isEscape = true;
        final int textLength = text.length();
        int nextChar = escapeStart + 1;
        while (nextChar < textLength && text.charAt(nextChar) == '\\') {
          isEscape = !isEscape;
          nextChar++;
        }
        if (!isEscape) {
          continue;
        }
        escapeStart = nextChar - 1;
        int length = 1;
        // see JLS 3.10.6. Escape Sequences for Character and String Literals
        boolean zeroToThree = false;
        while (escapeStart + length < textLength) {
          final char c = text.charAt(escapeStart + length);
          if (c < '0' || c > '9') {
            break;
          }
          if (length == 1 && (c == '0' || c == '1' || c == '2' || c == '3')) {
            zeroToThree = true;
          }
          if (c == '8' || c == '9' || (length > 2 && !zeroToThree) || length > 3) {
            registerErrorAtOffset(expression, escapeStart, length);
            break;
          }
          length++;
        }
      }
    }
  }
}