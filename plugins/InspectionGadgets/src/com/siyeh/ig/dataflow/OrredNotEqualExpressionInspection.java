/*
 * Copyright 2009 Bas Leijdekkers
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
package com.siyeh.ig.dataflow;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class OrredNotEqualExpressionInspection extends BaseInspection {

    @Override
    @Nls
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "orred.not.equal.expression.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "orred.not.equal.expression.problem.descriptor");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new OrredNotEqualExpressionFix();
    }

    private static class OrredNotEqualExpressionFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "orred.not.equal.expression.quickfix");
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiBinaryExpression)) {
                return;
            }
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) element;
            final PsiExpression lhs = binaryExpression.getLOperand();
            final String lhsText = lhs.getText();
            final PsiExpression rhs = binaryExpression.getROperand();
            if (rhs == null) {
                return;
            }
            final String rhsText = rhs.getText();
            replaceExpression(binaryExpression, lhsText + "&&" + rhsText);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new OrredNotEqualExpressionVisitor();
    }

    private static class OrredNotEqualExpressionVisitor
            extends BaseInspectionVisitor {

        private static final PsiReferenceExpression[] EMPTY_ARRAY = {};

        @Override
        public void visitBinaryExpression(PsiBinaryExpression expression) {
            final IElementType tokenType = expression.getOperationTokenType();
            if (JavaTokenType.OROR != tokenType) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression rhs = expression.getROperand();
            final PsiReferenceExpression[] lhsReferences = getReferences(lhs);
            final PsiReferenceExpression[] rhsReferences = getReferences(rhs);
            for (PsiReferenceExpression lhsReference : lhsReferences) {
                for (PsiReferenceExpression rhsReference : rhsReferences) {
                    if (lhsReference.resolve() == rhsReference.resolve()) {
                        registerError(expression);
                    }
                }
            }
        }

        private PsiReferenceExpression[] getReferences(
                PsiExpression expression) {
            if (!(expression instanceof PsiBinaryExpression)) {
                return EMPTY_ARRAY;
            }
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) expression;
            final IElementType tokenType =
                    binaryExpression.getOperationTokenType();
            if (JavaTokenType.NE != tokenType) {
                return EMPTY_ARRAY;
            }
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            if (lhs instanceof PsiReferenceExpression) {
                final PsiReferenceExpression lref =
                        (PsiReferenceExpression) lhs;
                if (rhs instanceof PsiReferenceExpression) {
                    final PsiReferenceExpression rref =
                            (PsiReferenceExpression) rhs;
                    return new PsiReferenceExpression[] {lref, rref};
                } else {
                    return new PsiReferenceExpression[] {lref};
                }
            } else if (rhs instanceof PsiReferenceExpression) {
                final PsiReferenceExpression rref =
                        (PsiReferenceExpression) rhs;
                return new PsiReferenceExpression[] {rref};
            }
            return EMPTY_ARRAY;
        }
    }
}
