/*
 * Copyright 2008 Bas Leijdekkers
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
package com.siyeh.ig.junit;

import com.intellij.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.Assert;

public class AssertEqualsMayBeAssertSameInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "assertequals.may.be.assertsame.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "assertequals.may.be.assertsame.problem.descriptor");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new AssertEqualsMayBeAssertSameFix();
    }

    private static class AssertEqualsMayBeAssertSameFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return "Replace with 'assertSame()'";
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement grandParent = element.getParent().getParent();
            if (!(grandParent instanceof PsiMethodCallExpression)) {
                return;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) grandParent;
            final String text = methodCallExpression.getText();
            final String newExpressionText =
                    text.replace("assertEquals", "assertSame");
            replaceExpression(methodCallExpression, newExpressionText);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AssertEqualsMayBeAssertSameVisitor();
    }

    private static class AssertEqualsMayBeAssertSameVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String name = methodExpression.getReferenceName();
            if (!"assertEquals".equals(name)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length == 3 && arguments.length == 2) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            final String qualifiedName = aClass.getQualifiedName();
            if (!"org.junit.Assert".equals(qualifiedName) &&
                    !"junit.framework.Assert".equals(qualifiedName)) {
                return;
            }
            final PsiExpression argument1 = arguments[arguments.length - 2];
            if (!couldBeAssertSameArgument(argument1)) {
                return;
            }
            final PsiExpression argument2 = arguments[arguments.length - 1];
            if (!couldBeAssertSameArgument(argument2)) {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean couldBeAssertSameArgument(
                PsiExpression argument1) {
            final PsiType type = argument1.getType();
            if (!(type instanceof PsiClassType)) {
                return false;
            }
            final PsiClassType classType = (PsiClassType) type;
            final PsiClass argumentClass = classType.resolve();
            if (argumentClass == null) {
                return false;
            }
            if (!argumentClass.hasModifierProperty(PsiModifier.FINAL)) {
                return false;
            }
            final PsiMethod[] methods =
                    argumentClass.findMethodsByName("equals", true);
            final PsiManager manager = argument1.getManager();
            final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(
                    manager.getProject());
            final PsiClass objectClass = psiFacade.findClass("java.lang.Object",
                    argumentClass.getResolveScope());
            if (objectClass == null) {
                return false;
            }
            for (PsiMethod psiMethod : methods) {
                final PsiClass containingClass = psiMethod.getContainingClass();
                if (!objectClass.equals(containingClass)) {
                    return false;
                }
            }
            return true;
        }
    }
}
