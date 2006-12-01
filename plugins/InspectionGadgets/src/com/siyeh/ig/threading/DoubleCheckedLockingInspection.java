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
package com.siyeh.ig.threading;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class DoubleCheckedLockingInspection extends ExpressionInspection {

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "double.checked.locking.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DoubleCheckedLockingVisitor();
    }

    private static class DoubleCheckedLockingVisitor
            extends BaseInspectionVisitor {

        public void visitIfStatement(@NotNull PsiIfStatement statement) {
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
            registerStatementError(statement);
        }
    }
}