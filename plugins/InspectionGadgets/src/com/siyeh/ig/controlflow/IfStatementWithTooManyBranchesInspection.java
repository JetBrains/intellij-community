/*
 * Copyright 2003-2005 Dave Griffith
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiStatement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class IfStatementWithTooManyBranchesInspection
        extends StatementInspection {

    private static final int DEFAULT_BRANCH_LIMIT = 3;

    /**
     * this is public for the DefaultJDOMExternalizer thingy
     * @noinspection PublicField
     */
    public int m_limit = DEFAULT_BRANCH_LIMIT;

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public JComponent createOptionsPanel() {
        return new SingleIntegerFieldOptionsPanel(
                InspectionGadgetsBundle.message(
                        "if.statement.with.too.many.branches.max.option"),
                this, "m_limit");
    }

    protected String buildErrorString(PsiElement location) {
        final PsiIfStatement statement = (PsiIfStatement)location.getParent();
        final int branches = calculateNumBranches(statement);
        return InspectionGadgetsBundle.message(
                "if.statement.with.too.many.branches.problem.descriptor",
                branches);
    }

    private static int calculateNumBranches(PsiIfStatement statement) {
        final PsiStatement branch = statement.getElseBranch();
        if (branch == null) {
            return 1;
        }
        if (!(branch instanceof PsiIfStatement)) {
            return 2;
        }
        return 1 + calculateNumBranches((PsiIfStatement)branch);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new IfStatementWithTooManyBranchesVisitor();
    }

    private class IfStatementWithTooManyBranchesVisitor
            extends StatementInspectionVisitor {

        public void visitIfStatement(@NotNull PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiElement parent = statement.getParent();
            if (parent instanceof PsiIfStatement) {
                final PsiIfStatement parentStatement = (PsiIfStatement)parent;
                final PsiStatement elseBranch = parentStatement.getElseBranch();
                if (statement.equals(elseBranch)) {
                    return;
                }
            }
            final int branches = calculateNumBranches(statement);
            if (branches <= m_limit) {
                return;
            }
            registerStatementError(statement);
        }
    }
}