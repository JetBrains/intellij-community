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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class IfStatementWithIdenticalBranchesInspection
        extends StatementInspection{

    private InspectionGadgetsFix fix = new CollapseIfFix();

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "if.statement.with.identical.branches.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message(
                "if.statement.with.identical.branches.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class CollapseIfFix extends InspectionGadgetsFix{

        public String getName(){
            return InspectionGadgetsBundle.message(
                    "if.statement.with.identical.branches.collapse.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement identifier = descriptor.getPsiElement();
            final PsiIfStatement statement =
                    (PsiIfStatement) identifier.getParent();
            assert statement != null;
            final PsiStatement thenBranch = statement.getThenBranch();
            if(thenBranch == null) {
                return;
            }
            final String bodyText = thenBranch.getText();
            replaceStatement(statement, bodyText);
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new IfStatementWithIdenticalBranchesVisitor();
    }

    private static class IfStatementWithIdenticalBranchesVisitor
            extends BaseInspectionVisitor{

        public void visitIfStatement(@NotNull PsiIfStatement statement){
            super.visitIfStatement(statement);
            final PsiStatement thenBranch = statement.getThenBranch();
            final PsiStatement elseBranch = statement.getElseBranch();
            if(thenBranch == null) {
                return;
            }
            if (!EquivalenceChecker.statementsAreEquivalent(
                    thenBranch, elseBranch)) {
                return;
            }
            registerStatementError(statement);
        }
    }
}