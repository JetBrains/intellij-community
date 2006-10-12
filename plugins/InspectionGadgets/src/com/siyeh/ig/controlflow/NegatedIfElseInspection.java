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
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class NegatedIfElseInspection extends StatementInspection {

    /** @noinspection PublicField*/
    public boolean m_ignoreNegatedNullComparison = true;

    public String getID() {
        return "IfStatementWithNegatedCondition";
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("negated.if.else.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "negated.if.else.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NegatedIfElseVisitor();
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
                "negated.if.else.ignore.option"),
                this, "m_ignoreNegatedNullComparison");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new NegatedIfElseFix();
    }

    private static class NegatedIfElseFix extends InspectionGadgetsFix {

        public String getName(){
            return InspectionGadgetsBundle.message(
                    "negated.if.else.invert.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement ifToken = descriptor.getPsiElement();
            final PsiIfStatement ifStatement =
                    (PsiIfStatement) ifToken.getParent();
            assert ifStatement != null;
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            if (elseBranch == null) {
                return;
            }
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            final PsiExpression condition = ifStatement.getCondition();
            if (condition == null) {
                return;
            }
            final String negatedCondition =
                    BoolUtils.getNegatedExpressionText(condition);
            String elseText = elseBranch.getText();
            final PsiElement lastChild = elseBranch.getLastChild();
            if (lastChild instanceof PsiComment ) {
                final PsiComment comment = (PsiComment)lastChild;
                final IElementType tokenType = comment.getTokenType();
                if (JavaTokenType.END_OF_LINE_COMMENT.equals(tokenType)) {
                    elseText += '\n';
                }
            }
            @NonNls final String newStatement = "if("+ negatedCondition + ')' +
                    elseText + " else " + thenBranch.getText();
            replaceStatement(ifStatement, newStatement);
        }
    }

    private class NegatedIfElseVisitor extends StatementInspectionVisitor {

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

            final PsiExpression condition = statement.getCondition();
            if (condition == null) {
                return;
            }
            if (!ExpressionUtils.isNegation(condition,
                    m_ignoreNegatedNullComparison)) {
                return;
            }
            final PsiElement parent = statement.getParent();
            if (parent instanceof PsiIfStatement) {
                return;
            }
            registerStatementError(statement);
        }
    }
}