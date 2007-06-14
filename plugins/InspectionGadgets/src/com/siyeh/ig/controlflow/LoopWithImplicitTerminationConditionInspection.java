/*
 * Copyright 2007 Bas Leijdekkers
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
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.BoolUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LoopWithImplicitTerminationConditionInspection
        extends BaseInspection {

    @Nls @NotNull
    public String getDisplayName() {
        return "Loop with implicit termination condition";
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return "<code>#ref</code> statement with implicit condition";
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new LoopWithImplicitTerminationConditionFix();
    }

    private static class LoopWithImplicitTerminationConditionFix
            extends InspectionGadgetsFix {

        @NotNull public String getName() {
            return "Make while condition explicit";
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiWhileStatement)) {
                return;
            }
            final PsiWhileStatement whileStatement = (PsiWhileStatement) parent;
            final PsiExpression whileCondition = whileStatement.getCondition();
            if (whileCondition == null) {
                return;
            }
            final PsiStatement body = whileStatement.getBody();
            final PsiStatement statement;
            if (body instanceof PsiBlockStatement) {
                final PsiBlockStatement blockStatement = (PsiBlockStatement) body;
                final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                final PsiStatement[] statements = codeBlock.getStatements();
                if (statements.length == 0) {
                    return;
                }
                statement = statements[0];
            } else {
                statement = body;
            }
            if (!(statement instanceof PsiIfStatement)) {
                return;
            }
            final PsiIfStatement ifStatement = (PsiIfStatement) statement;
            final PsiExpression ifCondition = ifStatement.getCondition();
            if (ifCondition == null) {
                return;
            }
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            if (containsUnlabeledBreakStatement(thenBranch)) {
                final String negatedExpressionText =
                        BoolUtils.getNegatedExpressionText(ifCondition);
                replaceExpression(whileCondition, negatedExpressionText);
                if (elseBranch == null) {
                    ifStatement.delete();
                } else {
                    ifStatement.replace(elseBranch);
                }
            } else if (containsUnlabeledBreakStatement(elseBranch)) {
                whileCondition.replace(ifCondition);
                if (thenBranch ==  null) {
                    ifStatement.delete();
                } else {
                    ifStatement.replace(thenBranch);
                }
            }
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LoopWithImplicitTerminationConditionVisitor();
    }

    private static class LoopWithImplicitTerminationConditionVisitor
            extends BaseInspectionVisitor {

        public void visitWhileStatement(PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            if (statement.getCondition() == null) {
                return;
            }
            final PsiStatement body = statement.getBody();
            final PsiElement bodyElement;
            if (body instanceof PsiBlockStatement) {
                final PsiBlockStatement blockStatement =
                        (PsiBlockStatement) body;
                final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                final PsiStatement[] statements = codeBlock.getStatements();
                if (statements.length == 0) {
                    return;
                }
                bodyElement = statements[0];
            } else {
                bodyElement = body;
            }
            if (!bodyElementIsImplicitCondition(bodyElement)) {
                return;
            }
            registerStatementError(statement);
        }

        private static boolean bodyElementIsImplicitCondition(
                PsiElement bodyElement) {
            if (!(bodyElement instanceof PsiIfStatement)) {
                return false;
            }
            final PsiIfStatement ifStatement = (PsiIfStatement) bodyElement;
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            if (containsUnlabeledBreakStatement(thenBranch)) {
                return true;
            }
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            return containsUnlabeledBreakStatement(elseBranch);
        }
    }

    static boolean containsUnlabeledBreakStatement(
            @Nullable PsiStatement thenBranch) {
        if (!(thenBranch instanceof PsiBlockStatement)) {
            return isUnlabeledBreakStatement(thenBranch);
        }
        final PsiBlockStatement blockStatement =
                (PsiBlockStatement) thenBranch;
        final PsiCodeBlock codeBlock =
                blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length != 1) {
            return false;
        }
        final PsiStatement statement = statements[0];
        return isUnlabeledBreakStatement(statement);
    }

    static boolean isUnlabeledBreakStatement(
            @Nullable PsiStatement statement) {
        if (!(statement instanceof PsiBreakStatement)) {
            return false;
        }
        final PsiBreakStatement breakStatement =
                (PsiBreakStatement) statement;
        final PsiIdentifier identifier =
                breakStatement.getLabelIdentifier();
        return identifier == null;
    }
}