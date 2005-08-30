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
package com.siyeh.ig.portability;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class SystemExitInspection extends ExpressionInspection {
    public String getID(){
        return "CallToSystemExit";
    }
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("system.exit.call.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.PORTABILITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiIdentifier methodNameIdentifier = (PsiIdentifier) location;
        final PsiReference methodExpression = (PsiReference) methodNameIdentifier.getParent();
        assert methodExpression != null;
        final PsiMethod method = (PsiMethod) methodExpression.resolve();
        assert method != null;
        final PsiClass containingClass = method.getContainingClass();
        assert containingClass != null;
        return InspectionGadgetsBundle.message("system.exit.call.problem.descriptor", containingClass.getName());
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SystemExitVisitor();
    }

    private static class SystemExitVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            if (!isSystemExit(expression)) {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean isSystemExit(PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();

            final String methodName = methodExpression.getReferenceName();
            @NonNls final String exit = "exit";
            @NonNls final String halt = "halt";
            if (!exit.equals(methodName) && !halt.equals(methodName)) {
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
            if (!parameterType.equals(PsiType.INT)) {
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
            return !(!"java.lang.System".equals(className) &&
                     !"java.lang.Runtime".equals(className));
        }
    }

}
