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
package com.siyeh.ig.assignment;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class IncrementDecrementUsedAsExpressionInspection
        extends BaseInspection {

    @NotNull
    public String getID() {
        return "ValueOfIncrementOrDecrementUsed";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "increment.decrement.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiElement location = (PsiElement)infos[0];
        if (location instanceof PsiPostfixExpression) {
            final PsiPostfixExpression postfixExpression =
                    (PsiPostfixExpression)location;
            final PsiJavaToken sign = postfixExpression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (tokenType.equals(JavaTokenType.PLUSPLUS)) {
                return InspectionGadgetsBundle.message(
                        "value.of.post.increment.problem.descriptor");
            } else {
                return InspectionGadgetsBundle.message(
                        "value.of.post.decrement.problem.descriptor");
            }
        } else {
            final PsiPrefixExpression prefixExpression =
                    (PsiPrefixExpression)location;
            final PsiJavaToken sign = prefixExpression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (tokenType.equals(JavaTokenType.PLUSPLUS)) {
                return InspectionGadgetsBundle.message(
                        "value.of.pre.increment.problem.descriptor");
            } else {
                return InspectionGadgetsBundle.message(
                        "value.of.pre.decrement.problem.descriptor");
            }
        }
    }


    public BaseInspectionVisitor buildVisitor() {
        return new IncrementDecrementUsedAsExpressionVisitor();
    }

    private static class IncrementDecrementUsedAsExpressionVisitor
            extends BaseInspectionVisitor {

        public void visitPostfixExpression(
                @NotNull PsiPostfixExpression expression) {
            super.visitPostfixExpression(expression);
            final PsiElement parent = expression.getParent();
            if(parent instanceof PsiExpressionStatement ||
                    (parent instanceof PsiExpressionList &&
                            parent.getParent() instanceof
                                    PsiExpressionListStatement)) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                    !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            registerError(expression, expression);
        }

        public void visitPrefixExpression(
                @NotNull PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);

            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiExpressionStatement ||
                    (parent instanceof PsiExpressionList &&
                            parent.getParent() instanceof
                                    PsiExpressionListStatement)) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                    !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            registerError(expression, expression);
        }
    }
}