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
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ForLoopReplaceableByWhileInspection extends StatementInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreLoopsWithoutConditions = false;
    private final ReplaceForByWhileFix fix = new ReplaceForByWhileFix();

    public String getID(){
        return "ForLoopReplaceableByWhile";
    }
    public String getDisplayName() {
        return "'for' loop may be replaced by 'while' loop";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'#ref' loop statement may be replace by 'while' loop #loc";
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel("Ignore 'infinite' for loops without conditions",
                                              this, "m_ignoreLoopsWithoutConditions");
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class ReplaceForByWhileFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace with 'while'";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement forKeywordElement = descriptor.getPsiElement();
            final PsiForStatement forStatement =
                    (PsiForStatement) forKeywordElement.getParent();
            assert forStatement != null;
            final PsiExpression condition = forStatement.getCondition();
            final PsiStatement body = forStatement.getBody();
            final String whileStatement;
            if (condition == null) {
                whileStatement = "while(true)" + body.getText();
            } else {
                whileStatement = "while(" + condition.getText() + ')' + body.getText();
            }
            replaceStatement(forStatement, whileStatement);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ForLoopReplaceableByWhileVisitor();
    }

    private  class ForLoopReplaceableByWhileVisitor extends StatementInspectionVisitor {


        public void visitForStatement(@NotNull PsiForStatement statement) {
            super.visitForStatement(statement);
            final PsiStatement initialization = statement.getInitialization();
            if (initialization != null && !(initialization instanceof PsiEmptyStatement)) {
                return;
            }
            final PsiStatement update = statement.getUpdate();
            if (update != null && !(update instanceof PsiEmptyStatement)) {
                return;
            }
            if(m_ignoreLoopsWithoutConditions)
            {
                final PsiExpression condition = statement.getCondition();
                if(condition == null){
                    return;
                }
                final String conditionText = condition.getText();
                if(PsiKeyword.TRUE.equals(conditionText)){
                    return;
                }
            }
            registerStatementError(statement);
        }
    }
}
