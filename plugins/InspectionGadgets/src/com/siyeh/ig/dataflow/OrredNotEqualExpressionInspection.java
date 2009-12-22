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

import com.intellij.openapi.util.Pair;
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
import org.jetbrains.annotations.Nullable;

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
    public boolean isEnabledByDefault() {
        return true;
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

        @Override
        public void visitBinaryExpression(PsiBinaryExpression expression) {
            final IElementType tokenType = expression.getOperationTokenType();
            if (JavaTokenType.OROR != tokenType) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression rhs = expression.getROperand();
            final Pair<PsiReferenceExpression, PsiExpression> pair1 =
                    getReferenceExpressionPair(lhs);
            final Pair<PsiReferenceExpression, PsiExpression> pair2 =
                    getReferenceExpressionPair(rhs);
            if (pair1 == null || pair2 == null) {
                return;
            }
            final PsiExpression expression1 = pair1.getSecond();
            final PsiExpression expression2 = pair2.getSecond();
            if (expression1 == null || expression2 == null) {
                return;
            }
            final Project project = expression1.getProject();
            final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
            final PsiConstantEvaluationHelper constantEvaluationHelper =
                    psiFacade.getConstantEvaluationHelper();
            final Object constant1 =
                    constantEvaluationHelper.computeConstantExpression(expression1);
            final Object constant2 =
                    constantEvaluationHelper.computeConstantExpression(expression2);
            if (constant1 == null || constant2 == null || constant1 == constant2) {
                return;
            }
            final PsiReferenceExpression referenceExpression1 = pair1.getFirst();
            final PsiReferenceExpression referenceExpression2 = pair2.getFirst();
            if (referenceExpression1.resolve() == referenceExpression2.resolve()) {
                registerError(expression);
            }
        }

        @Nullable
        private static Pair<PsiReferenceExpression, PsiExpression>
        getReferenceExpressionPair(PsiExpression expression) {
            if (!(expression instanceof PsiBinaryExpression)) {
                return null;
            }
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) expression;
            final IElementType tokenType =
                    binaryExpression.getOperationTokenType();
            if (JavaTokenType.NE != tokenType) {
                return null;
            }
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            if (lhs instanceof PsiReferenceExpression) {
                final PsiReferenceExpression lref =
                        (PsiReferenceExpression) lhs;
                return new Pair(lref, rhs);
            } else if (rhs instanceof PsiReferenceExpression) {
                final PsiReferenceExpression rref =
                        (PsiReferenceExpression) rhs;
                return new Pair(rref, lhs);
            }
            return null;
        }
    }
}
