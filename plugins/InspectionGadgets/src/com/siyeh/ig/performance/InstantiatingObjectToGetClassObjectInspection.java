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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class InstantiatingObjectToGetClassObjectInspection
        extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "instantiating.object.to.get.class.object.display.name");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "instantiating.object.to.get.class.object.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new InstantiatingObjectToGetClassObjectFix();
    }

    private static class InstantiatingObjectToGetClassObjectFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "instantiating.object.to.get.class.object.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiMethodCallExpression expression =
                    (PsiMethodCallExpression)descriptor.getPsiElement();
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            final PsiType type = qualifier.getType();
            if (type == null || !(type instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType)type;
            final String text = classType.getClassName();
            replaceExpression(expression, text + ".class");
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new InstantiatingObjectToGetClassObjectVisitor();
    }

    private static class InstantiatingObjectToGetClassObjectVisitor
            extends BaseInspectionVisitor {

        @Override public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if (!"getClass".equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] args = argumentList.getExpressions();
            if (args.length != 0) {
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (!(qualifier instanceof PsiNewExpression)) {
                return;
            }
            registerError(expression);
        }
    }
}