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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryLabelOnBreakStatementInspection
        extends StatementInspection {

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "unnecessary.label.on.break.statement.problem.descriptor");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new UnnecessaryLabelOnBreakStatementFix();
    }

    private static class UnnecessaryLabelOnBreakStatementFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "unnecessary.label.remove.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement breakKeywordElement = descriptor.getPsiElement();
            final PsiBreakStatement breakStatement =
                    (PsiBreakStatement)breakKeywordElement.getParent();
            final PsiIdentifier identifier =
                    breakStatement.getLabelIdentifier();
            if (identifier == null) {
                return;
            }
            identifier.delete();
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryLabelOnBreakStatementVisitor();
    }

    private static class UnnecessaryLabelOnBreakStatementVisitor
            extends StatementInspectionVisitor {

        public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
            final PsiIdentifier labelIdentifier =
                    statement.getLabelIdentifier();
            if (labelIdentifier == null) {
                return;
            }
            final String labelText = labelIdentifier.getText();
            if (labelText == null || labelText.length() == 0) {
                return;
            }
            final PsiStatement exitedStatement =
                    statement.findExitedStatement();
            if (exitedStatement == null) {
                return;
            }
            final PsiStatement labelEnabledParent =
                    PsiTreeUtil.getParentOfType(statement,
                            PsiForStatement.class, PsiDoWhileStatement.class,
                            PsiForeachStatement.class, PsiWhileStatement.class,
                            PsiSwitchStatement.class);
            if (labelEnabledParent == null) {
                return;
            }
            if (exitedStatement.equals(labelEnabledParent)) {
                registerStatementError(statement);
            }
        }
    }
}