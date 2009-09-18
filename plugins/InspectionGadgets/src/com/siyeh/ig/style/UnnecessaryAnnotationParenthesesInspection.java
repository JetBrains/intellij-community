/*
 * Copyright 2009 Bas Leijdekkers
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryAnnotationParenthesesInspection extends BaseInspection {

    @Override
    @Nls
    @NotNull()
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessary.annotation.parentheses.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "unnecessary.annotation.parentheses.problem.descriptor");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessaryAnnotationParenthesesFix();
    }

    private static class UnnecessaryAnnotationParenthesesFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "unnecessary.annotation.parameter.quickfix");
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiAnnotationParameterList)) {
                return;
            }
            final PsiElement[] children = element.getChildren();
            for (PsiElement child : children) {
                child.delete();
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryAnnotatinoParenthesesVisitor();
    }

    private static class UnnecessaryAnnotatinoParenthesesVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitAnnotation(PsiAnnotation annotation) {
            super.visitAnnotation(annotation);
            final PsiJavaCodeReferenceElement reference =
                    annotation.getNameReferenceElement();
            if (reference == null) {
                return;
            }
            final PsiAnnotationParameterList parameterList =
                    annotation.getParameterList();
            final PsiElement[] children = parameterList.getChildren();
            if (children.length == 0) {
                return;
            }
            final PsiNameValuePair[] nameValuePairs =
                    parameterList.getAttributes();
            if (nameValuePairs.length > 0) {
                return;
            }
            final PsiElement target = reference.resolve();
            if (!(target instanceof PsiClass)) {
                return;
            }
            final PsiClass annotationClass = (PsiClass) target;
            final PsiMethod[] methods = annotationClass.getMethods();
            for (PsiMethod method : methods) {
                if (!(method instanceof PsiAnnotationMethod)) {
                    continue;
                }
                final PsiAnnotationMethod annotationMethod =
                        (PsiAnnotationMethod) method;
                final PsiAnnotationMemberValue defaultValue =
                        annotationMethod.getDefaultValue();
                if (defaultValue == null) {
                    return;
                }
            }
            registerError(parameterList);
        }
    }
}
