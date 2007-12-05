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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DoubleCheckedLockingInspection extends BaseInspection {

    /** @noinspection PublicField*/
    public boolean ignoreOnVolatileVariables = false;

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "double.checked.locking.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "double.checked.locking.problem.descriptor");
    }

    @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
                "double.checked.locking.ignore.on.volatiles.option"), this, 
                "ignoreOnVolatileVariables"
        );
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DoubleCheckedLockingVisitor();
    }

    private class DoubleCheckedLockingVisitor
            extends BaseInspectionVisitor {

        @Override public void visitIfStatement(@NotNull PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiExpression outerCondition = statement.getCondition();
            if (outerCondition == null) {
                return;
            }
            if (SideEffectChecker.mayHaveSideEffects(outerCondition)) {
                return;
            }
            PsiStatement thenBranch = statement.getThenBranch();
            thenBranch = ControlFlowUtils.stripBraces(thenBranch);
            if (!(thenBranch instanceof PsiSynchronizedStatement)) {
                return;
            }
            final PsiSynchronizedStatement syncStatement =
                    (PsiSynchronizedStatement)thenBranch;
            final PsiCodeBlock body = syncStatement.getBody();
            if (body == null) {
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            if (statements.length != 1) {
                return;
            }
            if (!(statements[0] instanceof PsiIfStatement)) {
                return;
            }
            final PsiIfStatement innerIf = (PsiIfStatement)statements[0];
            final PsiExpression innerCondition = innerIf.getCondition();
            if (!EquivalenceChecker.expressionsAreEquivalent(innerCondition,
                    outerCondition)) {
                return;
            }
            if (ignoreOnVolatileVariables &&
                    ifStatementAssignsVolatileVariable(innerIf)) {
                return;
            }
            registerStatementError(statement);
        }

        private boolean ifStatementAssignsVolatileVariable(
                PsiIfStatement statement) {
            PsiStatement innerThen = statement.getThenBranch();
            innerThen = ControlFlowUtils.stripBraces(innerThen);
            if (!(innerThen instanceof PsiExpressionStatement)) {
                return false;
            }
            final PsiExpressionStatement expressionStatement =
                    (PsiExpressionStatement)innerThen;
            final PsiExpression expression =
                    expressionStatement.getExpression();
            if (!(expression instanceof PsiAssignmentExpression)) {
                return false;
            }
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression)expression;
            final PsiExpression lhs =
                    assignmentExpression.getLExpression();
            if (!(lhs instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression)lhs;
            final PsiElement element =
                    referenceExpression.resolve();
            if (!(element instanceof PsiField)) {
                return false;
            }
            final PsiField field = (PsiField)element;
            return field.hasModifierProperty(PsiModifier.VOLATILE);
        }
    }
}