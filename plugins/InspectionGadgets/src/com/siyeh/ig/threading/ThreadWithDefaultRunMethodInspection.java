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
package com.siyeh.ig.threading;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class ThreadWithDefaultRunMethodInspection extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "thread.with.default.run.method.display.name");
    }

    public String getID() {
        return "InstantiatingAThreadWithDefaultRunMethod";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "thread.with.default.run.method.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ThreadWithDefaultRunMethodVisitor();
    }

    private static class ThreadWithDefaultRunMethodVisitor
            extends BaseInspectionVisitor {

        public void visitNewExpression(@NotNull PsiNewExpression expression) {
            super.visitNewExpression(expression);
            final PsiAnonymousClass anonymousClass =
                    expression.getAnonymousClass();

            if (anonymousClass != null) {
                final PsiJavaCodeReferenceElement baseClassReference =
                        anonymousClass.getBaseClassReference();
                final PsiElement referent = baseClassReference.resolve();
                if (referent == null) {
                    return;
                }
                final PsiClass referencedClass = (PsiClass)referent;
                final String referencedClassName =
                        referencedClass.getQualifiedName();
                if (!"java.lang.Thread".equals(referencedClassName)) {
                    return;
                }
                if (definesRun(anonymousClass)) {
                    return;
                }
                final PsiExpressionList argumentList =
                        expression.getArgumentList();
                if (argumentList == null) {
                    return;
                }
                final PsiExpression[] args = argumentList.getExpressions();
                for (PsiExpression arg : args) {
                    if (TypeUtils.expressionHasTypeOrSubtype(
                            "java.lang.Runnable", arg)) {
                        return;
                    }
                }
                registerError(baseClassReference);
            } else {
                final PsiJavaCodeReferenceElement classReference =
                        expression.getClassReference();
                if (classReference == null) {
                    return;
                }
                final PsiElement referent = classReference.resolve();
                if (referent == null) {
                    return;
                }
                final PsiClass referencedClass = (PsiClass)referent;
                final String referencedClassName =
                        referencedClass.getQualifiedName();
                if (!"java.lang.Thread".equals(referencedClassName)) {
                    return;
                }
                final PsiExpressionList argumentList =
                        expression.getArgumentList();
                if (argumentList == null) {
                    return;
                }
                final PsiExpression[] args = argumentList.getExpressions();
                for (PsiExpression arg : args) {
                    if (TypeUtils.expressionHasTypeOrSubtype(
                            "java.lang.Runnable", arg)) {
                        return;
                    }
                }
                registerError(classReference);
            }
        }

        private static boolean definesRun(PsiAnonymousClass aClass) {
            final PsiMethod[] methods = aClass.getMethods();
            for (final PsiMethod method : methods) {
                final String methodName = method.getName();
                if (HardcodedMethodConstants.RUN.equals(methodName)) {
                    final PsiParameterList parameterList =
                            method.getParameterList();
                    final PsiParameter[] parameters =
                            parameterList.getParameters();
                    if (parameters.length == 0) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}