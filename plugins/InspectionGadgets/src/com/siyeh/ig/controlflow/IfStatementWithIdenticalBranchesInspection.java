/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public class IfStatementWithIdenticalBranchesInspection
        extends BaseInspection{

    @Override
    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "if.statement.with.identical.branches.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "if.statement.with.identical.branches.problem.descriptor");
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos){
        if (infos.length > 0) {
            final PsiIfStatement elseIfStatement = (PsiIfStatement) infos[0];
            return new CollapseIfFix();
        }
        return new CollapseIfFix();
    }

    private static class CollapseIfFix extends InspectionGadgetsFix{

      public CollapseIfFix() {
      }


        @NotNull
        public String getName(){
            return InspectionGadgetsBundle.message(
                    "if.statement.with.identical.branches.collapse.quickfix");
        }

        @Override
        public void doFix(@NotNull Project project,
                          ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement identifier = descriptor.getPsiElement();
            final PsiIfStatement statement =
                    (PsiIfStatement) identifier.getParent();
            assert statement != null;
            final PsiStatement thenBranch = statement.getThenBranch();
            if(thenBranch == null) {
                return;
            }
            final PsiStatement elseBranch = statement.getElseBranch();
            if (elseBranch == null) {
                // implicit else branch after the if
                statement.delete();
                return;
            }
          PsiIfStatement elseIfStatement = elseBranch instanceof PsiIfStatement ? (PsiIfStatement)elseBranch : null;
            if (elseIfStatement == null) {
                final PsiElement parent = statement.getParent();
                if (thenBranch instanceof PsiBlockStatement) {
                    final PsiBlockStatement blockStatement =
                            (PsiBlockStatement) thenBranch;
                    if (parent instanceof PsiCodeBlock) {
                        final PsiCodeBlock codeBlock =
                                blockStatement.getCodeBlock();
                        final PsiStatement[] statements = codeBlock.getStatements();
                        if (statements.length > 0) {
                            parent.addRangeBefore(statements[0],
                                    statements[statements.length -1], statement);
                        }
                        statement.delete();
                    } else {
                        statement.replace(blockStatement);
                    }
                } else {
                    statement.replace(thenBranch);
                }
            } else {
                final PsiExpression condition1 = statement.getCondition();
                final PsiExpression condition2 = elseIfStatement.getCondition();
                if (condition1 == null) {
                    return;
                }
                replaceExpression(condition1, buildOrExpressionText(
                        condition1, condition2));
                final PsiStatement elseElseBranch =
                        elseIfStatement.getElseBranch();
                if (elseElseBranch == null) {
                    elseIfStatement.delete();
                } else {
                    elseIfStatement.replace(elseElseBranch);
                }
            }
        }

        private static String buildOrExpressionText(PsiExpression expression1,
                                                    PsiExpression expression2) {
            final StringBuilder result = new StringBuilder();
            if (expression1 != null) {
                result.append(expression1.getText());
            }
            result.append("||");
            if (expression2 != null) {
                result.append(expression2.getText());
            }
            return result.toString();
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor(){
        return new IfStatementWithIdenticalBranchesVisitor();
    }

    private static class IfStatementWithIdenticalBranchesVisitor
            extends BaseInspectionVisitor{

        @Override public void visitIfStatement(
                @NotNull PsiIfStatement ifStatement){
            super.visitIfStatement(ifStatement);
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            final Project project = ifStatement.getProject();
            final InputVariables inputVariables =
                    new InputVariables(Collections.<PsiVariable>emptyList(),
                            project, new LocalSearchScope(thenBranch), false);
            final DuplicatesFinder finder =
                    new DuplicatesFinder(new PsiElement[]{thenBranch},
                            inputVariables, null,
                            Collections.<PsiVariable>emptyList());
            if (elseBranch instanceof PsiIfStatement) {
                final PsiIfStatement statement =
                        (PsiIfStatement) elseBranch;
                final PsiStatement branch = statement.getThenBranch();
                if (branch == null) {
                    return;
                }
                final Match match = finder.isDuplicate(branch, true);
                if (match != null) {
                    registerStatementError(ifStatement, statement);
                    return;
                }
            }
            if (elseBranch == null) {
                checkIfStatementWithoutElseBranch(ifStatement);
            } else {
                final Match match = finder.isDuplicate(elseBranch, true);
                if (match != null) {
                    registerStatementError(ifStatement);
                }
            }
        }

        private void checkIfStatementWithoutElseBranch(
                PsiIfStatement ifStatement) {
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            if (ControlFlowUtils.statementMayCompleteNormally(thenBranch)) {
                return;
            }
            PsiStatement nextStatement = getNextStatement(ifStatement);
            if (thenBranch instanceof PsiBlockStatement) {
                final PsiBlockStatement blockStatement =
                        (PsiBlockStatement) thenBranch;
                final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                final PsiStatement[] statements = codeBlock.getStatements();
                final PsiStatement lastStatement =
                        statements[statements.length - 1];
                for (PsiStatement statement : statements) {
                    if (nextStatement == null) {
                        if (statement == lastStatement &&
                                statement instanceof PsiReturnStatement) {
                            final PsiReturnStatement returnStatement =
                                    (PsiReturnStatement) statement;
                            if (returnStatement.getReturnValue() == null) {
                                registerStatementError(ifStatement);
                            }
                        }
                        return;
                    } else if (!EquivalenceChecker.statementsAreEquivalent(
                            statement, nextStatement)) {
                        return;
                    }
                    nextStatement = getNextStatement(nextStatement);
                }
            } else if (!EquivalenceChecker.statementsAreEquivalent(
                    thenBranch, nextStatement)) {
                return;
            }
            registerStatementError(ifStatement);
        }

        @Nullable
        private static PsiStatement getNextStatement(PsiStatement statement) {
            PsiStatement nextStatement =
                    PsiTreeUtil.getNextSiblingOfType(statement,
                            PsiStatement.class);
            while (nextStatement == null) {
                //noinspection AssignmentToMethodParameter
                statement = PsiTreeUtil.getParentOfType(statement,
                        PsiStatement.class);
                if (statement == null) {
                    return null;
                }
                if (statement instanceof PsiLoopStatement) {
                    // return in a loop statement is not the same as continuing
                    // looping.
                    return statement;
                }
                nextStatement = PsiTreeUtil.getNextSiblingOfType(statement,
                        PsiStatement.class);
                if (nextStatement == null) {
                    continue;
                }
                final PsiElement statementParent = statement.getParent();
                if (!(statementParent instanceof PsiIfStatement)) {
                    continue;
                }
                // nextStatement should not be the else part of an if statement
                final PsiElement nextStatementParent =
                        nextStatement.getParent();
                if (statementParent.equals(nextStatementParent)) {
                    nextStatement = null;
                }
            }
            return nextStatement;
        }
    }
}
