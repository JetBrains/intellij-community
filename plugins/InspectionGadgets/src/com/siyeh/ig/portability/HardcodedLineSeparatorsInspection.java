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
package com.siyeh.ig.portability;

import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class HardcodedLineSeparatorsInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "hardcoded.line.separator.display.name");
    }

    @NotNull
    public String getID(){
        return "HardcodedLineSeparator";
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "hardcoded.line.separator.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new HardcodedLineSeparatorsVisitor();
    }

    private static class HardcodedLineSeparatorsVisitor
            extends BaseInspectionVisitor {

        private static final char NEW_LINE_CHAR = '\n';
        private static final char RETURN_CHAR = '\r';

        @Override public void visitLiteralExpression(
                @NotNull PsiLiteralExpression expression) {
            super.visitLiteralExpression(expression);
            final PsiType type = expression.getType();
            if (type == null) {
                return;
            }
            if (TypeUtils.isJavaLangString(type)) {
                final String value = (String) expression.getValue();
                if (value == null) {
                    return;
                }
                if (value.indexOf(NEW_LINE_CHAR) >= 0 ||
                        value.indexOf(RETURN_CHAR) >= 0) {
                    registerError(expression);
                }
            } else if (type.equals(PsiType.CHAR)) {
                final Character value = (Character) expression.getValue();
                if (value == null) {
                    return;
                }
                final char unboxedValue = value.charValue();
                if (unboxedValue == NEW_LINE_CHAR
                        || unboxedValue == RETURN_CHAR) {
                    registerError(expression);
                }
            }
        }
    }
}
