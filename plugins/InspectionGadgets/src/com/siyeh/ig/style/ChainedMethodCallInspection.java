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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class ChainedMethodCallInspection extends BaseInspection {

    /** @noinspection PublicField */
    public boolean m_ignoreFieldInitializations = true;

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "chained.method.call.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "chained.method.call.problem.descriptor");
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "chained.method.call.ignore.option"),
                this, "m_ignoreFieldInitializations");
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ChainedMethodCallVisitor();
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new ChainedMethodCallFix();
    }

    private static class ChainedMethodCallFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "introduce.variable.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor) {
            final RefactoringActionHandlerFactory factory =
                    RefactoringActionHandlerFactory.getInstance();
            final RefactoringActionHandler introduceHandler =
                    factory.createIntroduceVariableHandler();
            final PsiElement methodNameElement = descriptor.getPsiElement();
            final PsiReferenceExpression methodCallExpression =
                    (PsiReferenceExpression)methodNameElement.getParent();
            assert methodCallExpression != null;
            final PsiExpression qualifier =
                    methodCallExpression.getQualifierExpression();
            final DataManager dataManager = DataManager.getInstance();
            final DataContext dataContext = dataManager.getDataContext();
            introduceHandler.invoke(project, new PsiElement[] {qualifier},
                    dataContext);
        }
    }

    private class ChainedMethodCallVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression reference =
                    expression.getMethodExpression();
            final PsiExpression qualifier = reference.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            if (!isCallExpression(qualifier)) {
                return;
            }
            if (m_ignoreFieldInitializations) {
                final PsiElement field =
                        PsiTreeUtil.getParentOfType(expression, PsiField.class);
                if (field != null) {
                    return;
                }
            }
            registerMethodCallError(expression);
        }

        private boolean isCallExpression(PsiExpression expression) {
            expression = ParenthesesUtils.stripParentheses(expression);
            return expression instanceof PsiMethodCallExpression ||
                   expression instanceof PsiNewExpression;
        }
    }
}