/*
 * Copyright 2003-2005 Dave Griffith
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
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;

public class StringCompareToInspection extends ExpressionInspection {
    public String getID(){
        return "CallToStringCompareTo";
    }
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("string.compareto.call.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("string.compareto.call.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StringCompareToVisitor();
    }

    private static class StringCompareToVisitor extends BaseInspectionVisitor {


        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }

            if (!isStringCompareTo(expression)) {
                return;
            }

            registerMethodCallError(expression);
        }

        private static boolean isStringCompareTo(PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return false;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.COMPARE_TO.equals(methodName)) {
                return false;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return false;
            }
            final PsiParameterList paramList = method.getParameterList();
            if (paramList == null) {
                return false;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            if (parameters.length != 1) {
                return false;
            }
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
