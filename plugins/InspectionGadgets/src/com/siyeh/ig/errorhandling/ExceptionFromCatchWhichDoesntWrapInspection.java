/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ExceptionFromCatchWhichDoesntWrapInspection
        extends BaseInspection {

    /** @noinspection PublicField*/
    public boolean ignoreGetMessage = false;

    @Override @NotNull
    public String getID() {
        return "ThrowInsideCatchBlockWhichIgnoresCaughtException";
    }

    @Override @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "exception.from.catch.which.doesnt.wrap.display.name");
    }

    @Override @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "exception.from.catch.which.doesnt.wrap.problem.descriptor");
    }

    @Override @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
                "exception.from.catch.which.doesntwrap.ignore.option"), this,
                "ignoreGetMessage");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ExceptionFromCatchWhichDoesntWrapVisitor();
    }

    private class ExceptionFromCatchWhichDoesntWrapVisitor
            extends BaseInspectionVisitor {

        @Override public void visitThrowStatement(PsiThrowStatement statement) {
            super.visitThrowStatement(statement);
            if (!isInCatchBlock(statement)) {
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

        private boolean argumentsContainsCatchParameter(
                PsiExpression[] arguments) {
            final ReferenceFinder visitor = new ReferenceFinder();
            for (PsiExpression argument : arguments) {
                argument.accept(visitor);
                if (visitor.doArgumentsContainCatchParameter()) {
                    return true;
                }
            }
            return false;
        }

        public boolean isInCatchBlock(@NotNull PsiElement element){
            final PsiCatchSection catchSection =
                    PsiTreeUtil.getParentOfType(element, PsiCatchSection.class,
                            true, PsiClass.class);
            if (catchSection == null) {
                return false;
            }
            final PsiParameter parameter = catchSection.getParameter();
            if (parameter == null) {
                return false;
            }
            @NonNls final String parameterName = parameter.getName();
            return !("ignore".equals(parameterName) ||
                    "ignored".equals(parameterName));
        }
    }

    private class ReferenceFinder extends JavaRecursiveElementVisitor {

        private boolean argumentsContainCatchParameter = false;

        @Override
        public void visitReferenceExpression(
                PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiElement referent = expression.resolve();
            if (!(referent instanceof PsiParameter)) {
                return;
            }
            final PsiParameter parameter = (PsiParameter)referent;
            final PsiElement declarationScope =
                    parameter.getDeclarationScope();
            if (!(declarationScope instanceof PsiCatchSection)) {
                return;
            }
            if (ignoreGetMessage) {
                argumentsContainCatchParameter = true;
            } else {
                final PsiElement parent = expression.getParent();
                final PsiElement grandParent = parent.getParent();
                if (!(grandParent instanceof PsiMethodCallExpression)) {
                    argumentsContainCatchParameter = true;
                }
            }
        }

        public boolean doArgumentsContainCatchParameter() {
            return argumentsContainCatchParameter;
        }
    }
}