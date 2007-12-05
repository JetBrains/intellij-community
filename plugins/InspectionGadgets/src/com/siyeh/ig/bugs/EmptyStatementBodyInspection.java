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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class EmptyStatementBodyInspection extends BaseInspection {

    /** @noinspection PublicField*/
    public boolean m_reportEmptyBlocks = false;

    @NotNull
    public String getID(){
        return "StatementWithEmptyBody";
    }

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "statement.with.empty.body.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "statement.with.empty.body.problem.descriptor");
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "statement.with.empty.body.include.option"),
                this, "m_reportEmptyBlocks");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EmptyStatementVisitor();
    }

    private class EmptyStatementVisitor extends BaseInspectionVisitor {

        @Override public void visitDoWhileStatement(
                @NotNull PsiDoWhileStatement statement) {
            super.visitDoWhileStatement(statement);
            if (PsiUtil.isInJspFile(statement.getContainingFile())) {
                return;
            }
            final PsiStatement body = statement.getBody();
            if (body == null || !isEmpty(body)) {
                return;
            }
            registerStatementError(statement);
        }

        @Override public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            if (PsiUtil.isInJspFile(statement.getContainingFile())) {
                return;
            }
            final PsiStatement body = statement.getBody();
            if (body == null || !isEmpty(body)) {
                return;
            }
            registerStatementError(statement);
        }

        @Override public void visitForStatement(@NotNull PsiForStatement statement) {
            super.visitForStatement(statement);
            if (PsiUtil.isInJspFile(statement.getContainingFile())) {
                return;
            }
            final PsiStatement body = statement.getBody();
            if (body == null || !isEmpty(body)) {
                return;
            }
            registerStatementError(statement);
        }

        @Override public void visitForeachStatement(
                @NotNull PsiForeachStatement statement) {
            super.visitForeachStatement(statement);
            if (PsiUtil.isInJspFile(statement.getContainingFile())) {
                return;
            }
            final PsiStatement body = statement.getBody();
            if (body == null || !isEmpty(body)) {
                return;
            }
            registerStatementError(statement);
        }

        @Override public void visitIfStatement(@NotNull PsiIfStatement statement) {
            super.visitIfStatement(statement);
            if (PsiUtil.isInJspFile(statement.getContainingFile())) {
                return;
            }
            final PsiStatement thenBranch = statement.getThenBranch();
            if (thenBranch != null && isEmpty(thenBranch)) {
                registerStatementError(statement);
                return;
            }
            final PsiStatement elseBranch = statement.getElseBranch();
            if (elseBranch != null && isEmpty(elseBranch)) {
                final PsiElement elseToken = statement.getElseElement();
                if (elseToken == null) {
                    return;
                }
                registerError(elseToken);
            }
        }

        private boolean isEmpty(PsiElement body) {
            if (body instanceof PsiEmptyStatement) {
                return true;
            } else if (m_reportEmptyBlocks &&
                    body instanceof PsiBlockStatement) {
                final PsiBlockStatement block = (PsiBlockStatement) body;
                final PsiCodeBlock codeBlock = block.getCodeBlock();
                return codeBlockIsEmpty(codeBlock);
            } else if (body instanceof PsiCodeBlock) {
                final PsiCodeBlock codeBlock = (PsiCodeBlock) body;
                return codeBlockIsEmpty(codeBlock);
            }
            return false;
        }

        private boolean codeBlockIsEmpty(PsiCodeBlock codeBlock) {
            final PsiStatement[] statements = codeBlock.getStatements();
            return statements.length == 0;
        }
    }
}