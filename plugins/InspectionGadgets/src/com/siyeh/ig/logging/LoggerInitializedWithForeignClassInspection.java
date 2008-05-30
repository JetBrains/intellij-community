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
package com.siyeh.ig.logging;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class LoggerInitializedWithForeignClassInspection
        extends BaseInspection {

    Logger log = Logger.getLogger(String.class);

    @NotNull
    public String getDisplayName() {
        return "Logger initialized with foreign class";
        //return InspectionGadgetsBundle.message("multiple.loggers.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return "Logger initializer with foreign class <code>#ref</code>";
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new LoggerInitializedWithForeignClassFix((String)infos[0]);
    }

    private static class LoggerInitializedWithForeignClassFix
            extends InspectionGadgetsFix {

        private final String newClassName;

        private LoggerInitializedWithForeignClassFix(String newClassName) {
            this.newClassName = newClassName;
        }

        @NotNull
        public String getName() {
            return "replace with'" + newClassName + ".class'";
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiClassObjectAccessExpression)) {
                return;
            }
            final PsiClassObjectAccessExpression classObjectAccessExpression =
                    (PsiClassObjectAccessExpression) element;
            replaceExpression(classObjectAccessExpression,
                    newClassName + ".class");
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LoggerInitializedWithForeignClassVisitor();
    }

    private static class LoggerInitializedWithForeignClassVisitor
            extends BaseInspectionVisitor {

        public void visitClassObjectAccessExpression(
                PsiClassObjectAccessExpression expression) {
            super.visitClassObjectAccessExpression(expression);
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiExpressionList)) {
                return;
            }
            final PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiMethodCallExpression)) {
                return;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) grandParent;
            final PsiClass containingClass = PsiTreeUtil.getParentOfType(
                    expression, PsiClass.class);
            if (containingClass == null) {
                return;
            }
            final String containingClassName = containingClass.getName();
            if (containingClassName == null) {
                return;
            }
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final String referenceName = methodExpression.getReferenceName();
            if (!"getLogger".equals(referenceName)) {
                return;
            }
            final PsiMethod method = methodCallExpression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            final String className = aClass.getQualifiedName();
            if (!"org.apache.log4j.Logger".equals(className)) {
                return;
            }
            final PsiTypeElement operand = expression.getOperand();
            final PsiType type = operand.getType();
            if (!(type instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType) type;
            final PsiClass initializerClass = classType.resolve();
            if (initializerClass == null) {
                return;
            }
            if (containingClass.equals(initializerClass)) {
                return;
            }
            registerError(expression, containingClassName);
        }
    }
}
