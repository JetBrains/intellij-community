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
package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.JComponent;

public class StringConstructorInspection extends ExpressionInspection {

    /** @noinspection PublicField*/
    public boolean ignoreSubstringArguments = false;

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "string.constructor.display.name");
    }

    public String getID() {
        return "RedundantStringConstructorCall";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "string.constructor.problem.descriptor");
    }

    @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
                "string.constructor.substring.parameter.option"), this,
                "ignoreSubstringArguments");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StringConstructorVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new StringConstructorFix((PsiNewExpression)location);
    }

    private static class StringConstructorFix extends InspectionGadgetsFix {

        private final String m_name;

        private StringConstructorFix(PsiNewExpression expression) {
            super();
            final PsiExpressionList argList = expression.getArgumentList();
            assert argList != null;
            final PsiExpression[] args = argList.getExpressions();
            if (args.length == 1) {
                m_name = InspectionGadgetsBundle.message(
                        "string.constructor.replace.arg.quickfix");
            } else {
                m_name = InspectionGadgetsBundle.message(
                        "string.constructor.replace.empty.quickfix");
            }
        }

        public String getName() {
            return m_name;
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiNewExpression expression =
                    (PsiNewExpression)descriptor.getPsiElement();
            final PsiExpressionList argList = expression.getArgumentList();
            assert argList != null;
            final PsiExpression[] args = argList.getExpressions();
            final String argText;
            if (args.length == 1) {
                argText = args[0].getText();
            } else {
                argText = "\"\"";
            }
            replaceExpression(expression, argText);
        }
    }

    private class StringConstructorVisitor
            extends BaseInspectionVisitor {

        public void visitNewExpression(@NotNull PsiNewExpression expression) {
            super.visitNewExpression(expression);
            final PsiType type = expression.getType();
            if (!TypeUtils.isJavaLangString(type)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length > 1) {
                return;
            }
            if (arguments.length == 1) {
                final PsiExpression argument = arguments[0];
                final PsiType parameterType = argument.getType();
                if (!TypeUtils.isJavaLangString(parameterType)) {
                    return;
                }
                if (ignoreSubstringArguments) {
                    if (argument instanceof PsiMethodCallExpression) {
                        final PsiMethodCallExpression methodCallExpression =
                                (PsiMethodCallExpression)argument;
                        final PsiReferenceExpression methodExpression =
                                methodCallExpression.getMethodExpression();
                        final PsiElement element = methodExpression.resolve();
                        if (element instanceof PsiMethod) {
                            final PsiMethod method = (PsiMethod)element;
                            final PsiClass aClass = method.getContainingClass();
                            final String className = aClass.getQualifiedName();
                            @NonNls final String methodName = method.getName();
                            if ("java.lang.String".equals(className) &&
                                    methodName.equals("substring")) {
                                return;
                            }
                        }
                    }
                }
            }
            registerError(expression);
        }
    }
}