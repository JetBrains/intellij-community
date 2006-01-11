/*
 * Copyright 2006 Bas Leijdekkers
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

import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiBinaryExpression;
import org.jetbrains.annotations.Nullable;

public class LoopConditionNotUpdatedInsideLoopInspection
        extends StatementInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "loop.condition.not.updated.inside.loop.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    @Nullable
    protected String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message(
                "loop.condition.not.updated.inside.loop.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LoopConditionNotUpdatedInsideLoopVisitor();
    }

    private static class LoopConditionNotUpdatedInsideLoopVisitor
            extends BaseInspectionVisitor {

        public void visitWhileStatement(PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            final PsiExpression condition = statement.getCondition();
            checkCondition(condition);
            if (!(condition instanceof PsiBinaryExpression)) {
                return;
            }
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)condition;
            checkCondition(binaryExpression);
        }

        private void checkCondition(
                PsiExpression expression) {
            //if ()
            //final PsiExpression lhs = expression.getLOperand();
        }
    }
}