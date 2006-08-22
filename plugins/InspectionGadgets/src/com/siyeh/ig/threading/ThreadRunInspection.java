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
package com.siyeh.ig.threading;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class ThreadRunInspection extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("thread.run.display.name");
    }

    public String getID() {
        return "CallToThreadRun";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message("thread.run.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new ThreadRunFix();
    }

    private static class ThreadRunFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "thread.run.replace.quickfix");
        }

        public void doFix(@NotNull Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement methodNameIdentifier = descriptor.getPsiElement();
            final PsiReferenceExpression methodExpression =
                    (PsiReferenceExpression)methodNameIdentifier.getParent();
            assert methodExpression != null;
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier == null) {
                replaceExpression(methodExpression, "start");
            } else {
                final String qualifierText = qualifier.getText();
                replaceExpression(methodExpression, qualifierText + ".start");
            }
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ThreadRunVisitor();
    }

    private static class ThreadRunVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.RUN.equals(methodName)) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiParameterList paramList = method.getParameterList();
            final PsiParameter[] parameters = paramList.getParameters();
            if (parameters.length != 0) {
                return;
            }
            final PsiClass methodClass = method.getContainingClass();
            if (methodClass == null) {
                return;
            }
            if (!ClassUtils.isSubclass(methodClass, "java.lang.Thread")) {
                return;
            }
            if (isInsideThreadRun(expression)) {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean isInsideThreadRun(
                PsiElement element) {
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            if (method == null) {
                return false;
            }
            final String methodName = method.getName();
            if (!HardcodedMethodConstants.RUN.equals(methodName)) {
                return false;
            }
            final PsiClass methodClass = method.getContainingClass();
            if (methodClass == null) {
                return false;
            }
            return ClassUtils.isSubclass(methodClass, "java.lang.Thread");
        }
    }
}