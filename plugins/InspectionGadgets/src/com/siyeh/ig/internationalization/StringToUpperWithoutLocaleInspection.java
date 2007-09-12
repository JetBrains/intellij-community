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
package com.siyeh.ig.internationalization;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringToUpperWithoutLocaleInspection extends BaseInspection {

    @NotNull
    public String getID(){
        return "StringToUpperCaseOrToLowerCaseWithoutLocale";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "string.touppercase.tolowercase.without.locale.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "string.touppercase.tolowercase.without.locale.problem.descriptor");
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        final PsiReferenceExpression methodExpression =
                (PsiReferenceExpression)location.getParent();
        final PsiModifierListOwner annotatableQualifier =
                NonNlsUtils.getAnnotatableQualifier(
                        methodExpression);
        if (annotatableQualifier == null) {
            return null;
        }
        return new DelegatingFix(new AddAnnotationFix(
                AnnotationUtil.NON_NLS, annotatableQualifier));
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StringToUpperWithoutLocaleVisitor();
    }

    private static class StringToUpperWithoutLocaleVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.TO_UPPER_CASE.equals(methodName) &&
                    !HardcodedMethodConstants.TO_LOWER_CASE.equals(methodName)) {
                return;
            }
            if (NonNlsUtils.isNonNlsAnnotatedUse(expression)) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() == 1) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null) {
                return;
            }
            final String className = containingClass.getQualifiedName();
            if(!"java.lang.String".equals(className)) {
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (NonNlsUtils.isNonNlsAnnotated(qualifier)) {
                return;
            }
            registerMethodCallError(expression);
        }
    }
}