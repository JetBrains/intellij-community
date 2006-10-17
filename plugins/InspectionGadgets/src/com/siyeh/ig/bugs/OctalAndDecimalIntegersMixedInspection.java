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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class OctalAndDecimalIntegersMixedInspection
        extends ExpressionInspection {

    public String getID() {
        return "OctalAndDecimalIntegersInSameArray";
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "octal.and.decimal.integers.in.same.array.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.NUMERIC_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "octal.and.decimal.integers.in.same.array.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new OctalAndDecimalIntegersMixedVisitor();
    }

    private static class OctalAndDecimalIntegersMixedVisitor
            extends BaseInspectionVisitor {

        public void visitArrayInitializerExpression(
                PsiArrayInitializerExpression expression) {
            super.visitArrayInitializerExpression(expression);
            final PsiExpression[] initializers = expression.getInitializers();
            boolean hasDecimalLiteral = false;
            boolean hasOctalLiteral = false;
            for(final PsiExpression initializer : initializers){
                if(initializer instanceof PsiLiteralExpression){
                    final PsiLiteralExpression literal =
                            (PsiLiteralExpression) initializer;
                    if(isDecimalLiteral(literal)){
                        hasDecimalLiteral = true;
                    }
                    if(isOctalLiteral(literal)){
                        hasOctalLiteral = true;
                    }
                }
            }
            if (hasOctalLiteral && hasDecimalLiteral) {
                registerError(expression);
            }
        }

        private static boolean isDecimalLiteral(PsiLiteralExpression literal) {
            final PsiType type = literal.getType();
            if (!PsiType.INT.equals(type) &&
                !PsiType.LONG.equals(type)) {
                return false;
            }
            final String text = literal.getText();
            if ("0".equals(text)) {
                return false;
            }
            return text.charAt(0) != '0';
        }

        private static boolean isOctalLiteral(PsiLiteralExpression literal) {
            final PsiType type = literal.getType();
            if (!PsiType.INT.equals(type) && !PsiType.LONG.equals(type)) {
                return false;
            }
            @NonNls final String text = literal.getText();
            if ("0".equals(text) || "0L".equals(text)) {
                return false;
            }
            return text.charAt(0) == '0' && !text.startsWith("0x") &&
                    !text.startsWith("0X");
        }
    }
}