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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import org.jetbrains.annotations.NotNull;

public class ConstantOnRHSOfComparisonInspection extends BaseInspection {

    @NotNull
    public String getID() {
        return "ConstantOnRightSideOfComparison";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "constant.on.rhs.of.comparison.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "constant.on.rhs.of.comparison.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConstantOnRHSOfComparisonVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new SwapComparisonFix();
    }

    private static class SwapComparisonFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message("flip.comparision.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiBinaryExpression expression =
                    (PsiBinaryExpression) descriptor.getPsiElement();
            final PsiExpression rhs = expression.getROperand();
            final PsiExpression lhs = expression.getLOperand();
            final PsiJavaToken sign = expression.getOperationSign();
            assert rhs != null;
            final String rhsText = rhs.getText();
            final String flippedComparison =
                    ComparisonUtils.getFlippedComparison(sign);
            final String lhsText = lhs.getText();
            replaceExpression(expression,
                    rhsText + ' ' + flippedComparison + ' ' + lhsText);
        }
    }

    private static class ConstantOnRHSOfComparisonVisitor
            extends BaseInspectionVisitor {

        @Override public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null)) {
                return;
            }
            if (!ComparisonUtils.isComparison(expression)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression rhs = expression.getROperand();
            if (!PsiUtil.isConstantExpression(rhs) ||
                    PsiUtil.isConstantExpression(lhs)) {
                return;
            }
            registerError(expression);
        }
    }
}