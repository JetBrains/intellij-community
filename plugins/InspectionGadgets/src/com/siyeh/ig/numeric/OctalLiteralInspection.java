/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class OctalLiteralInspection extends BaseInspection {

    @NotNull
    public String getID() {
        return "OctalInteger";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("octal.literal.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "octal.literal.problem.descriptor");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new OctalLiteralVisitor();
    }

    private static class OctalLiteralVisitor extends BaseInspectionVisitor {

        @Override public void visitLiteralExpression(
                @NotNull PsiLiteralExpression literal) {
            super.visitLiteralExpression(literal);
            final PsiType type = literal.getType();
            if (type == null) {
                return;
            }
            if (!(type.equals(PsiType.INT)
                    || type.equals(PsiType.LONG))) {
                return;
            }
            @NonNls final String text = literal.getText();
            if ("0".equals(text) || "0L".equals(text) || "0l".equals(text)) {
                return;
            }
            if (text.charAt(0) != '0') {
                return;
            }
            if (text.startsWith("0x") || text.startsWith("0X")) {
                return;
            }
            registerError(literal);
        }
    }
}