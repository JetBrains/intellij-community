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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ComparisonToNaNInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "comparison.to.nan.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiBinaryExpression comparison = (PsiBinaryExpression)infos[0];
        final PsiJavaToken sign = comparison.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if (tokenType.equals(JavaTokenType.EQEQ)) {
            return InspectionGadgetsBundle.message(
                    "comparison.to.nan.problem.descriptor1");
        } else {
            return InspectionGadgetsBundle.message(
                    "comparison.to.nan.problem.descriptor2");
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ComparisonToNaNVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new ComparisonToNaNFix();
    }

    private static class ComparisonToNaNFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "comparison.to.nan.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiReferenceExpression NaNExpression =
                    (PsiReferenceExpression)descriptor.getPsiElement();
            final PsiElement qualifier = NaNExpression.getQualifier();
            if (qualifier == null) {
                return;
            }
            final String typeString = qualifier.getText();
            final PsiBinaryExpression comparison =
                    (PsiBinaryExpression)NaNExpression.getParent();
            final PsiExpression lhs = comparison.getLOperand();
            final PsiExpression rhs = comparison.getROperand();
            final PsiExpression operand;
            if (NaNExpression.equals(lhs)) {
                operand = rhs;
            } else {
                operand = lhs;
            }
            assert operand != null;
            final String operandText = operand.getText();
            final PsiJavaToken sign = comparison.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final String negationString;
            if (tokenType.equals(JavaTokenType.EQEQ)) {
                negationString = "";
            } else {
                negationString = "!";
            }
            @NonNls final String newExpressionText = negationString +
                    typeString + ".isNaN(" + operandText + ')';
            replaceExpression(comparison, newExpressionText);
        }
    }

    private static class ComparisonToNaNVisitor extends BaseInspectionVisitor {

        @Override public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if (!(expression.getROperand() != null)) {
                return;
            }
            if (!ComparisonUtils.isEqualityComparison(expression)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression rhs = expression.getROperand();
            if (!isFloatingPointType(lhs) && !isFloatingPointType(rhs)) {
                return;
            }
            if (isNaN(lhs)) {
                registerError(lhs, expression);
            } else if (rhs != null && isNaN(rhs)) {
                registerError(rhs, expression);
            }
        }

        private static boolean isFloatingPointType(PsiExpression expression) {
            if (expression == null) {
                return false;
            }
            final PsiType type = expression.getType();
            if (type == null) {
                return false;
            }
            return PsiType.DOUBLE.equals(type) || PsiType.FLOAT.equals(type);
        }

        private static boolean isNaN(PsiExpression expression) {
            if (!(expression instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression)expression;
            @NonNls final String referenceName =
                    referenceExpression.getReferenceName();
            if (!"NaN".equals(referenceName)) {
                return false;
            }
            final PsiElement qualifier = referenceExpression.getQualifier();
            if (qualifier == null) {
                return false;
            }
            @NonNls final String qualifierText = qualifier.getText();
            return "Double".equals(qualifierText) ||
                    "Float" .equals(qualifierText);
        }
    }
}