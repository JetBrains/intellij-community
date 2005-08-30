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
package com.siyeh.ig.maturity;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;

public class ThreadDumpStackInspection extends ExpressionInspection {
    public String getID(){
        return "CallToThreadDumpStack";
    }
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("dumpstack.call.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.MATURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("dumpstack.call.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ThreadDumpStackVisitor();
    }

    private static class ThreadDumpStackVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final String methodName = MethodCallUtils.getMethodName(expression);
            if (!HardcodedMethodConstants.DUMP_STACKTRACE.equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            if (argumentList.getExpressions().length != 0) {
                return;
            }
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final PsiElement element = methodExpression.resolve();
            if (!(element instanceof PsiMethod)) {
                return;
            }
            final PsiMethod method = (PsiMethod) element;
            final PsiClass aClass = method.getContainingClass();
            if(aClass == null)
            {
                return;
            }
            final String qualifiedName = aClass.getQualifiedName();
            if (!"java.lang.Thread".equals(qualifiedName)) {
                return;
            }
            registerMethodCallError(expression);
        }

    }

}
