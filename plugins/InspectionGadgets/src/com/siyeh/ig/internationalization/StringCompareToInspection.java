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
package com.siyeh.ig.internationalization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

public class StringCompareToInspection extends ExpressionInspection {

    public String getID() {
        return "CallToStringCompareTo";
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "string.compareto.call.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "string.compareto.call.problem.descriptor");
    }

    @Nullable
    protected InspectionGadgetsFix[] buildFixes(PsiElement location) {
        final List<InspectionGadgetsFix> result = new ArrayList();
        final PsiReferenceExpression methodExpression =
                (PsiReferenceExpression)location.getParent();
        final PsiModifierListOwner annotatableQualifier =
                AnnotateQualifierFix.extractAnnotatableQualifier(
                        methodExpression);
        if (annotatableQualifier != null) {
            result.add(new AnnotateQualifierFix(annotatableQualifier));
        }
        final PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression)methodExpression.getParent();
        final PsiModifierListOwner annotatableArgument =
                AnnotateArgumentFix.extractAnnotatableArgument(
                        methodCallExpression);
        if (annotatableArgument != null) {
            result.add(new AnnotateArgumentFix(annotatableArgument));
        }
        return result.toArray(new InspectionGadgetsFix[result.size()]);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StringCompareToVisitor();
    }

    private static class StringCompareToVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!isStringCompareTo(expression)) {
                return;
            }
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (InternationalizationUtil.isNonNlsAnnotated(qualifier)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return;
            }
            if (InternationalizationUtil.isNonNlsAnnotated(arguments[0])) {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean isStringCompareTo(
                PsiMethodCallExpression expression) {
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return false;
            }
            if (!MethodUtils.isCompareTo(method)) {
                return false;
            }
            final PsiParameterList paramList = method.getParameterList();
            final PsiParameter[] parameters = paramList.getParameters();
            final PsiType parameterType = parameters[0].getType();
            if (!TypeUtils.isJavaLangObject(parameterType) &&
                    !TypeUtils.isJavaLangString(parameterType)) {
                return false;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return false;
            }
            final String className = aClass.getQualifiedName();
            return "java.lang.String".equals(className);
        }
    }
}