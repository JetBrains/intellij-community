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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FallthruInSwitchStatementInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "fallthru.in.switch.statement.display.name");
    }

    @NotNull
    public String getID() {
        return "fallthrough";
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "fallthru.in.switch.statement.problem.descriptor");
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new FallthruInSwitchStatementFix();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new FallthroughInSwitchStatementVisitor();
    }

    private static class FallthruInSwitchStatementFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "fallthru.in.switch.statement.quickfix");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiSwitchLabelStatement labelStatement =
                    (PsiSwitchLabelStatement) descriptor.getPsiElement();
            final PsiManager manager = labelStatement.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiStatement breakStatement =
                    factory.createStatementFromText("break;", labelStatement);
            final PsiElement parent = labelStatement.getParent();
            parent.addBefore(breakStatement, labelStatement);
        }
    }

    private static class FallthroughInSwitchStatementVisitor
            extends BaseInspectionVisitor {

        @Override public void visitSwitchStatement(
                @NotNull PsiSwitchStatement statement) {
            super.visitSwitchStatement(statement);
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return;
            }
            boolean switchLabelValid = true;
            final PsiStatement[] statements = body.getStatements();
            for (final PsiStatement child : statements) {
                if (child instanceof PsiSwitchLabelStatement) {
                    if (!switchLabelValid) {
                        registerError(child);
                    }
                    switchLabelValid = true;
                } else {
                    switchLabelValid =
                            !ControlFlowUtils.statementMayCompleteNormally(
                                    child);
                }
            }
        }
    }
}