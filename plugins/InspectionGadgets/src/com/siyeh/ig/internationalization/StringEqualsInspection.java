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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class StringEqualsInspection extends BaseInspection {

    @NotNull
    public String getID() {
        return "CallToStringEquals";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "string.equals.call.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "string.equals.call.problem.descriptor");
    }

    @NotNull
    protected InspectionGadgetsFix[] buildFixes(PsiElement location) {
        final List<InspectionGadgetsFix> result = new ArrayList();
        final PsiReferenceExpression methodExpression =
                (PsiReferenceExpression)location.getParent();
        final PsiModifierListOwner annotatableQualifier =
                NonNlsUtils.getAnnotatableQualifier(
                        methodExpression);
        if (annotatableQualifier != null) {
            final InspectionGadgetsFix fix =
                    new DelegatingFix(new AddAnnotationFix(
                            AnnotationUtil.NON_NLS, annotatableQualifier));
            result.add(fix);
        }
        final PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression)methodExpression.getParent();
        final PsiModifierListOwner annotatableArgument =
                NonNlsUtils.getAnnotatableArgument(
                        methodCallExpression);
        if (annotatableArgument != null) {
            final InspectionGadgetsFix fix =
                    new DelegatingFix(new AddAnnotationFix(
                            AnnotationUtil.NON_NLS, annotatableArgument));
            result.add(fix);
        }
        return result.toArray(new InspectionGadgetsFix[result.size()]);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StringEqualsVisitor();
    }

    private static class StringEqualsVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!MethodCallUtils.isEqualsCall(expression)) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiParameterList paramList = method.getParameterList();
            final PsiParameter[] parameters = paramList.getParameters();
            final PsiType parameterType = parameters[0].getType();
            if (!TypeUtils.isJavaLangObject(parameterType)) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            final String className = aClass.getQualifiedName();
            if (!"java.lang.String".equals(className)) {
                return;
            }
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (NonNlsUtils.isNonNlsAnnotated(qualifier)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return;
            }
            if (NonNlsUtils.isNonNlsAnnotated(arguments[0])) {
                return;
            }
            registerMethodCallError(expression);
        }
    }
}