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
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class ConditionalExpressionWithIdenticalBranchesInspection
        extends ExpressionInspection {

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "conditional.expression.with.identical.branches.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new CollapseConditional();
    }

    private static class CollapseConditional extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "conditional.expression.with.identical.branches.collapse.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiConditionalExpression expression =
                    (PsiConditionalExpression)descriptor.getPsiElement();

            final PsiExpression thenExpression = expression.getThenExpression();
            assert thenExpression != null;
            final String bodyText = thenExpression.getText();
            replaceExpression(expression, bodyText);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConditionalExpressionWithIdenticalBranchesVisitor();
    }

    private static class ConditionalExpressionWithIdenticalBranchesVisitor
            extends BaseInspectionVisitor {

        public void visitConditionalExpression(
                PsiConditionalExpression expression) {
            super.visitConditionalExpression(expression);
            final PsiExpression thenExpression = expression.getThenExpression();
            final PsiExpression elseExpression = expression.getElseExpression();
            if (EquivalenceChecker.expressionsAreEquivalent(thenExpression,
                    elseExpression)) {
                registerError(expression);
            }
        }
    }
}