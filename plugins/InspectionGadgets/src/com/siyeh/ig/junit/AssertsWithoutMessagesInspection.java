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
package com.siyeh.ig.junit;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class AssertsWithoutMessagesInspection extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "asserts.without.messages.display.name");
    }

    @Override
    @NotNull
    public String getID() {
        return "MessageMissingOnJUnitAssertion";
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "asserts.without.messages.problem.descriptor");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new AssertionsWithoutMessagesVisitor();
    }

    private static class AssertionsWithoutMessagesVisitor
            extends BaseInspectionVisitor {

        @NonNls private static final Set<String> s_assertMethods =
                new HashSet<String>(8);

        static {
            s_assertMethods.add("assertTrue");
            s_assertMethods.add("assertFalse");
            s_assertMethods.add("assertEquals");
            s_assertMethods.add("assertNull");
            s_assertMethods.add("assertNotNull");
            s_assertMethods.add("assertSame");
            s_assertMethods.add("assertNotSame");
            s_assertMethods.add("fail");
        }

        @Override public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!isJUnitAssertion(expression)) {
                return;
            }
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final PsiMethod method = (PsiMethod)methodExpression.resolve();
            if (method == null) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            @NonNls final String methodName = method.getName();
            final int parameterCount = parameterList.getParametersCount();
            if (parameterCount < 2 && methodName.startsWith("assert")) {
                registerMethodCallError(expression);
                return;
            }
            if (parameterCount < 1) {
                registerMethodCallError(expression);
                return;
            }
            final PsiManager psiManager = expression.getManager();
            final Project project = psiManager.getProject();
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            final PsiType stringType = PsiType.getJavaLangString(psiManager,
                    scope);
            final PsiParameter[] parameters = parameterList.getParameters();
            final PsiType parameterType1 = parameters[0].getType();
            if (parameterType1.equals(stringType)) {
                if (parameters.length == 2) {
                    final PsiType parameterType2 = parameters[1].getType();
                    if (parameterType2.equals(stringType)) {
                        registerMethodCallError(expression);
                    }
                }
            } else {
                registerMethodCallError(expression);
            }
        }

        private static boolean isJUnitAssertion(
                PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!s_assertMethods.contains(methodName)) {
                return false;
            }
            final PsiMethod method = (PsiMethod)methodExpression.resolve();
            if (method == null) {
                return false;
            }
            final PsiClass targetClass = method.getContainingClass();
            return InheritanceUtil.isInheritor(targetClass,
                    "junit.framework.Assert") ||
                    InheritanceUtil.isInheritor(targetClass,
                            "org.junit.Assert");
        }
    }
}