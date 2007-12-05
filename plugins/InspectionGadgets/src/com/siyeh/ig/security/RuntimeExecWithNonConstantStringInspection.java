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
package com.siyeh.ig.security;

import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RuntimeExecWithNonConstantStringInspection
        extends BaseInspection {

    @NotNull
    public String getID() {
        return "CallToRuntimeExecWithNonConstantString";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "runtime.exec.call.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "runtime.exec.with.non.constant.string.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new RuntimeExecVisitor();
    }

    private static class RuntimeExecVisitor extends BaseInspectionVisitor {

        @Override public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if (!"exec".equals(methodName)) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            final String className = aClass.getQualifiedName();
            if (!"java.lang.Runtime".equals(className)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] args = argumentList.getExpressions();
            if (args.length == 0) {
                return;
            }
            final PsiExpression arg = args[0];
            final PsiType type = arg.getType();
            if (type == null) {
                return;
            }
            final String typeText = type.getCanonicalText();
            if (!"java.lang.String".equals(typeText)) {
                return;
            }
            final String stringValue =
                    (String)ConstantExpressionUtil.computeCastTo(arg, type);
            if (stringValue != null) {
                return;
            }
            registerMethodCallError(expression);
        }
    }
}
