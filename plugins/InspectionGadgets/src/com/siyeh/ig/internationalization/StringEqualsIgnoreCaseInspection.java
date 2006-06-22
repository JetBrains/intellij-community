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
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

public class StringEqualsIgnoreCaseInspection extends ExpressionInspection {

    public String getID(){
        return "CallToStringEqualsIgnoreCase";
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "string.equalsignorecase.call.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "string.equalsignorecase.call.problem.descriptor");
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
        return new StringEqualsIgnoreCaseVisitor();
    }

    private static class StringEqualsIgnoreCaseVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.EQUALS_IGNORE_CASE.equals(
                    methodName)) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiParameterList paramList = method.getParameterList();
            final PsiParameter[] parameters = paramList.getParameters();
            if (parameters.length != 1) {
                return;
            }
            final PsiType parameterType = parameters[0].getType();
            if (!TypeUtils.isJavaLangString(parameterType)) {
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
    }
}