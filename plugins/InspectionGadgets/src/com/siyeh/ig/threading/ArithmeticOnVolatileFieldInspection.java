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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;

public class ArithmeticOnVolatileFieldInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "arithmetic.on.volatile.field.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "arithmetic.on.volatile.field.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AritmeticOnVolatileFieldInspection();
    }

    private static class AritmeticOnVolatileFieldInspection
            extends BaseInspectionVisitor {

        @Override public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if (expression.getROperand() == null) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!JavaTokenType.ASTERISK.equals(tokenType) &&
                    !JavaTokenType.DIV.equals(tokenType) &&
                    !JavaTokenType.PLUS.equals(tokenType) &&
                    !JavaTokenType.MINUS.equals(tokenType) &&
                    !JavaTokenType.PERC.equals(tokenType)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            checkForVolatile(lhs);
            final PsiExpression rhs = expression.getROperand();
            checkForVolatile(rhs);
        }

        @Override public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            if (!WellFormednessUtils.isWellFormed(expression)) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!JavaTokenType.ASTERISKEQ.equals(tokenType) &&
                    !JavaTokenType.DIVEQ.equals(tokenType) &&
                    !JavaTokenType.PLUSEQ.equals(tokenType) &&
                    !JavaTokenType.MINUSEQ.equals(tokenType) &&
                    !JavaTokenType.PERCEQ.equals(tokenType)) {
                return;
            }
            final PsiExpression lhs = expression.getLExpression();
            checkForVolatile(lhs);
            final PsiExpression rhs = expression.getRExpression();
            checkForVolatile(rhs);
        }

        private void checkForVolatile(PsiExpression expression) {
            if (!(expression instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression reference =
                    (PsiReferenceExpression)expression;
            final PsiElement referent = reference.resolve();
            if (!(referent instanceof PsiField)) {
                return;
            }
            final PsiField field = (PsiField)referent;
            if (field.hasModifierProperty(PsiModifier.VOLATILE)) {
                registerError(expression);
            }
        }
    }
}