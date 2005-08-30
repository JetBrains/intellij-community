/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class HardcodedLineSeparatorsInspection extends ExpressionInspection {
    private static final int NEW_LINE_CHAR = (int) '\n';
    private static final int RETURN_CHAR = (int) '\r';

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("hardcoded.line.separator.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.PORTABILITY_GROUP_NAME;
    }

    public String getID(){
        return "HardcodedLineSeparator";
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("hardcoded.line.separator.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new HardcodedLineSeparatorsVisitor();
    }

    private static class HardcodedLineSeparatorsVisitor extends BaseInspectionVisitor {

        public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
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
                if ((value) == NEW_LINE_CHAR
                        || (value) == RETURN_CHAR) {
                    registerError(expression);
                }
            }
        }
    }
}
