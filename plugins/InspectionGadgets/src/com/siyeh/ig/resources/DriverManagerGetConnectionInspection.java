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
package com.siyeh.ig.resources;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;

public class DriverManagerGetConnectionInspection extends ExpressionInspection {
    public String getID(){
        return "CallToDriverManagerGetConnection";
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("drivermanager.call.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.RESOURCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("drivermanager.call.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DriverManagerGetConnectionVisitor();
    }

    private static class DriverManagerGetConnectionVisitor extends BaseInspectionVisitor {


        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            if (!isDriverManagerGetConnection(expression)) {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean isDriverManagerGetConnection(PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();

            final String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.GET_CONNECTION.equals(methodName) ) {
                return false;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return false;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return false;
            }
            final String className = aClass.getQualifiedName();
            if (className == null) {
                return false;
            }
            return "java.sql.DriverManager".equals(className);
        }
    }

}
