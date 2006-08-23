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
package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class ObjectAllocationInLoopInspection extends ExpressionInspection {

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "object.allocation.in.loop.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ObjectAllocationInLoopsVisitor();
    }

    private static class ObjectAllocationInLoopsVisitor
            extends BaseInspectionVisitor {

        public void visitNewExpression(@NotNull PsiNewExpression expression) {
            super.visitNewExpression(expression);
            if (!ControlFlowUtils.isInLoop(expression)) {
                return;
            }
            if (ControlFlowUtils.isInExitStatement(expression)) {
                return;
            }
            final PsiStatement newExpressionStatement =
                    PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
            if (newExpressionStatement == null) {
                return;
            }
            final PsiStatement parentStatement =
                    PsiTreeUtil.getParentOfType(newExpressionStatement,
                            PsiStatement.class);
            if (!ControlFlowUtils.statementMayCompleteNormally(
                    parentStatement)) {
                return;
            }
            if (isAllocatedOnlyOnce(expression)) {
                return;
            }
            registerError(expression);
        }

        private static boolean isAllocatedOnlyOnce(
                PsiNewExpression expression) {
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiAssignmentExpression)) {
                return false;
            }
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) parent;
            final PsiExpression lExpression =
                    assignmentExpression.getLExpression();
            if (!(lExpression instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiIfStatement ifStatement =
                    PsiTreeUtil.getParentOfType(assignmentExpression,
                            PsiIfStatement.class);
            if (ifStatement == null) {
                return false;
            }
            final PsiExpression condition = ifStatement.getCondition();
            if (!(condition instanceof PsiBinaryExpression)) {
                return false;
            }
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) condition;
            if (binaryExpression.getOperationTokenType() !=
                    JavaTokenType.EQEQ) {
                return false;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) lExpression;
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            if (lhs instanceof PsiLiteralExpression) {
                if (!"null".equals(lhs.getText())) {
                    return false;
                }
                if (!(rhs instanceof PsiReferenceExpression)) {
                    return false;
                }
                return referenceExpression.getText().equals(rhs.getText());
            } else if (rhs instanceof PsiLiteralExpression) {
                if (!"null".equals(rhs.getText())) {
                    return false;
                }
                if (!(lhs instanceof PsiReferenceExpression)) {
                    return false;
                }
                return referenceExpression.getText().equals(lhs.getText());
            } else {
                return false;
            }
        }
    }
}