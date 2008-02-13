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
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NestedMethodCallInspection extends BaseInspection {

    /** @noinspection PublicField */
    public boolean m_ignoreFieldInitializations = true;

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "nested.method.call.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "nested.method.call.problem.descriptor");
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "nested.method.call.ignore.option"),
                this, "m_ignoreFieldInitializations");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NestedMethodCallVisitor();
    }

    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new NestedMethodCallFix();
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    private static class NestedMethodCallFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "introduce.variable.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor) {
            final JavaRefactoringActionHandlerFactory factory =
                    JavaRefactoringActionHandlerFactory.getInstance();
            final RefactoringActionHandler introduceHandler =
                    factory.createIntroduceVariableHandler();
            final PsiElement methodNameElement = descriptor.getPsiElement();
            final PsiElement methodExpression = methodNameElement.getParent();
            if (methodExpression == null) {
                return;
            }
            final PsiElement methodCallExpression =
                    methodExpression.getParent();
            final DataManager dataManager = DataManager.getInstance();
            final DataContext dataContext = dataManager.getDataContext();
            introduceHandler.invoke(project,
                    new PsiElement[]{methodCallExpression}, dataContext);
        }
    }

    private class NestedMethodCallVisitor extends BaseInspectionVisitor {

        @Override public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            PsiExpression outerExpression = expression;
            while (outerExpression != null &&
                    outerExpression.getParent() instanceof PsiExpression) {
                outerExpression = (PsiExpression)outerExpression.getParent();
            }
            if (outerExpression == null) {
                return;
            }
            final PsiElement parent = outerExpression.getParent();
            if (!(parent instanceof PsiExpressionList)) {
                return;
            }
            final PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiCallExpression)) {
                return;
            }
            if (grandParent instanceof PsiMethodCallExpression) {

                final PsiMethodCallExpression surroundingCall =
                        (PsiMethodCallExpression)grandParent;
                final PsiReferenceExpression methodExpression =
                        surroundingCall.getMethodExpression();
                final String callName = methodExpression.getReferenceName();
                if (PsiKeyword.THIS.equals(callName) ||
                        PsiKeyword.SUPER.equals(callName)) {
                    //ignore nested method calls at the start of a constructor,
                    //where they can't be extracted
                    return;
                }
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
    }
}