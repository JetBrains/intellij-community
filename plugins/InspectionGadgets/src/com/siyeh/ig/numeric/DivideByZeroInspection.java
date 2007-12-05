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

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class DivideByZeroInspection extends BaseInspection {

    @NotNull
    public String getID() {
        return "divzero";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("divide.by.zero.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "divide.by.zero.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DivisionByZeroVisitor();
    }

    private static class DivisionByZeroVisitor extends BaseInspectionVisitor {

        @Override public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            final PsiExpression rhs = expression.getROperand();
            if (rhs == null) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.DIV) &&
                    !tokenType.equals(JavaTokenType.PERC)) {
                return;
            }
            final Object value =
                    ConstantExpressionUtil.computeCastTo(rhs, PsiType.DOUBLE);
            if (value == null || !(value instanceof Double)) {
                return;
            }
            final double constantValue = ((Double)value).doubleValue();
            if (constantValue == 0.0 || constantValue == -0.0) {
                registerError(expression);
            }
        }

        @Override public void visitAssignmentExpression(
                PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            final PsiExpression rhs = expression.getRExpression();
            if (rhs == null) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.DIVEQ)
                    && !tokenType.equals(JavaTokenType.PERCEQ)) {
                return;
            }
            final Object value = ConstantExpressionUtil.computeCastTo(rhs,
                    PsiType.DOUBLE);
            if (value == null || !(value instanceof Double)) {
                return;
            }
            final double constantValue = ((Double)value).doubleValue();
            if (constantValue == 0.0 || constantValue == -0.0) {
                registerError(expression);
            }
        }
    }
}