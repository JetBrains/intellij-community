/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiLiteralExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class ConfusingOctalEscapeInspection extends ExpressionInspection {

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
    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
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

        public void visitLiteralExpression(@NotNull PsiLiteralExpression exp) {
            super.visitLiteralExpression(exp);
            if (!TypeUtils.expressionHasType("java.lang.String", exp)) {
                return;
            }
            final String text = exp.getText();
            if (!containsConfusingOctalEscape(text)) {
                return;
            }
            registerError(exp);
        }

        private static boolean containsConfusingOctalEscape(String text) {
            int escapeStart = -1;
            while (true) {
                escapeStart = text.indexOf((int) '\\', escapeStart + 1);
                if (escapeStart < 0) {
                    return false;
                }
                if (escapeStart > 0 && text.charAt(escapeStart - 1) == '\\') {
                    if (escapeStart > 1) {
                        if (text.charAt(escapeStart - 2) != '\\') {
                            continue;
                        }
                    } else {
                        continue;
                    }
                }
                int digitPosition = escapeStart + 1;
                while (digitPosition < text.length() &&
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