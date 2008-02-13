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
package com.siyeh.ig.style;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnnecessarySuperQualifierInspection extends BaseInspection {

    @Nls
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessary.super.qualifier.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "unnecessary.super.qualifier.problem.descriptor"
        );
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessarySuperQualifierFix();
    }

    private static class UnnecessarySuperQualifierFix
            extends InspectionGadgetsFix {
        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "unnecessary.super.qualifier.quickfix");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            element.delete();
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessarySuperQualifierVisitor();
    }

    private static class UnnecessarySuperQualifierVisitor
            extends BaseInspectionVisitor {

        public void visitSuperExpression(PsiSuperExpression expression) {
            super.visitSuperExpression(expression);
            final PsiJavaCodeReferenceElement qualifier =
                    expression.getQualifier();
            if (qualifier != null) {
                return;
            }
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) parent;
            final PsiElement grandParent = referenceExpression.getParent();
            if (grandParent instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression)grandParent;
                if (!hasUnnecessarySuperQualifier(methodCallExpression)) {
                    return;
                }
            } else {
                if (!hasUnnecessarySuperQualifier(referenceExpression)) {
                    return;
                }
            }
            registerError(expression);
        }

        private static boolean hasUnnecessarySuperQualifier(
                PsiReferenceExpression referenceExpression) {
            final PsiClass parentClass =
                    PsiTreeUtil.getParentOfType(referenceExpression,
                            PsiClass.class);
            if (parentClass == null) {
                return false;
            }
            final PsiElement target = referenceExpression.resolve();
            if (target == null) {
                return false;
            }
            if (!(target instanceof PsiField)) {
                return false;
            }
            final PsiField superField = (PsiField)target;
            final String name = superField.getName();
            if (name == null) {
                return false;
            }
            final PsiField field = parentClass.findFieldByName(name, false);
            return field == null;
        }

        private static boolean hasUnnecessarySuperQualifier(
                PsiMethodCallExpression methodCallExpression) {
            final PsiMethod superMethod =
                    methodCallExpression.resolveMethod();
            if (superMethod == null) {
                return false;
            }
            final PsiClass parentClass =
                    PsiTreeUtil.getParentOfType(methodCallExpression,
                            PsiClass.class);
            if (parentClass == null) {
                return false;
            }
            final PsiMethod[] methods =
                    parentClass.findMethodsBySignature(superMethod, false);
            return methods.length <= 0;
        }
    }
}