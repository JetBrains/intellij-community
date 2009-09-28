/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class ConfusingOctalEscapeInspection extends BaseInspection {

    @NotNull
    public String getID() {
        return "ConfusingOctalEscapeSequence";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "confusing.octal.escape.sequence.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "confusing.octal.escape.sequence.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConfusingOctalEscapeVisitor();
    }

    private static class ConfusingOctalEscapeVisitor
            extends BaseInspectionVisitor {

        @Override public void visitLiteralExpression(
                @NotNull PsiLiteralExpression expression) {
            super.visitLiteralExpression(expression);
            if (!TypeUtils.expressionHasType("java.lang.String", expression)) {
                return;
            }
            final String text = expression.getText();
            if (!containsConfusingOctalEscape(text)) {
                return;
            }
            registerError(expression);
        }

        private static boolean containsConfusingOctalEscape(String text) {
            int escapeStart = -1;
            while (true) {
                escapeStart = text.indexOf((int) '\\', escapeStart + 1);
                if (escapeStart < 0) {
                    return false;
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
                int digitPosition = escapeStart + 1;
                while (digitPosition < textLength &&
                        Character.isDigit(text.charAt(digitPosition))) {
                    digitPosition++;
                }
                if (digitPosition > escapeStart + 1) {
                    final String escapeString = text.substring(escapeStart + 1,
                                                               digitPosition);
                    if (escapeString.length() > 3) {
                        return true;
                    }
                    if (escapeString.indexOf((int) '8') > 0 ||
                            escapeString.indexOf((int) '9') > 0) {
                        return true;
                    }
                }
            }
        }
    }
}