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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class ExceptionFromCatchWhichDoesntWrapInspection
        extends StatementInspection {

    public String getID() {
        return "ThrowInsideCatchBlockWhichIgnoresCaughtException";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "exception.from.catch.which.doesnt.wrap.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ExceptionFromCatchWhichDoesntWrapVisitor();
    }

    private static class ExceptionFromCatchWhichDoesntWrapVisitor
            extends StatementInspectionVisitor {

        public void visitThrowStatement(PsiThrowStatement statement) {
            super.visitThrowStatement(statement);
            if (!ControlFlowUtils.isInCatchBlock(statement)) {
                return;
            }
            final PsiExpression exception = statement.getException();
            if (!(exception instanceof PsiNewExpression)) {
                return;
            }
            final PsiNewExpression newExpression = (PsiNewExpression)exception;
            final PsiMethod constructor = newExpression.resolveConstructor();
            if (constructor == null) {
                return;
            }
            final PsiExpressionList argumentList =
                    newExpression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (argumentsContainsCatchParameter(arguments)) {
                return;
            }
            registerStatementError(statement);
        }

        private static boolean argumentsContainsCatchParameter(
                PsiExpression[] arguments) {
            for (final PsiExpression argument : arguments) {
                if (argument instanceof PsiReferenceExpression) {
                    final PsiReferenceExpression referenceExpression =
                            (PsiReferenceExpression)argument;
                    final PsiElement referent = referenceExpression.resolve();
                    if (referent instanceof PsiParameter) {
                        final PsiParameter parameter = (PsiParameter)referent;
                        if (parameter.getDeclarationScope()
                                instanceof PsiCatchSection) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
