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
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfusingElseInspection extends StatementInspection {

    public String getID() {
        return "ConfusingElseBranch";
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("confusing.else.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "confusing.else.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConfusingElseVisitor();
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new ConfusingElseFix();
    }

    private static class ConfusingElseFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "confusing.else.unwrap.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement ifKeyword = descriptor.getPsiElement();
            final PsiIfStatement ifStatement =
                    (PsiIfStatement)ifKeyword.getParent();
            assert ifStatement != null;
            final PsiExpression condition = ifStatement.getCondition();
            final String conditionText;
            if (condition == null) {
                conditionText = "";
            } else {
                conditionText = condition.getText();
            }
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            @NonNls final String text = "if(" + conditionText + ')' +
                    thenBranch.getText();
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            if (elseBranch == null) {
                return;
            }
            if (elseBranch instanceof PsiBlockStatement) {
                final PsiBlockStatement elseBlock =
                        (PsiBlockStatement)elseBranch;
                final PsiCodeBlock block = elseBlock.getCodeBlock();
                final PsiElement[] children = block.getChildren();
                if (children.length > 2) {
                    final PsiElement containingElement =
                            ifStatement.getParent();
                    assert containingElement != null;
                    containingElement.addRangeAfter(children[1],
                            children[children.length - 2], ifStatement);
                }
            } else {
                final PsiElement containingElement = ifStatement.getParent();
                assert containingElement != null;
                containingElement.addAfter(elseBranch, ifStatement);
            }
            replaceStatement(ifStatement, text);
        }
    }

    private static class ConfusingElseVisitor
            extends StatementInspectionVisitor {

        public void visitIfStatement(@NotNull PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiStatement thenBranch = statement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            final PsiStatement elseBranch = statement.getElseBranch();
            if (elseBranch == null) {
                return;
            }
            if (elseBranch instanceof PsiIfStatement) {
                return;
            }
            if (ControlFlowUtils.statementMayCompleteNormally(thenBranch)) {
                return;
            }
            final PsiStatement nextStatement =
                    PsiTreeUtil.getNextSiblingOfType(statement,
                            PsiStatement.class);
            if (nextStatement == null) {
                return;
            }
            if (!ControlFlowUtils.statementMayCompleteNormally(elseBranch)) {
                return;
                //protecting against an edge case where both branches return
                // and are followed by a case label
            }
            final PsiElement elseToken = statement.getElseElement();
            if (elseToken == null) {
                return;
            }
            registerError(elseToken);
        }
    }
}