/*
 * Copyright 2006-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ReplaceAllDotInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "replace.all.dot.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "replace.all.dot.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SystemSetSecurityManagerVisitor();
    }

    private static class SystemSetSecurityManagerVisitor
            extends BaseInspectionVisitor {

        @Override public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if (!"replaceAll".equals(methodName)) {
                return;
            }
            final PsiExpressionList argList = expression.getArgumentList();
            final PsiExpression[] args = argList.getExpressions();
            if (args.length != 2) {
                return;
            }
            final PsiExpression arg = args[0];
            if (!PsiUtil.isConstantExpression(arg)) {
                return;
            }
            final PsiType argType = arg.getType();
            if (argType == null) {
                return;
            }
            if (!"java.lang.String".equals(argType.getCanonicalText())) {
                return;
            }
            final String argValue =
                    (String) ConstantExpressionUtil.computeCastTo(arg, argType);
            if (!".".equals(argValue)) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            final String qualifiedName = containingClass.getQualifiedName();
            if (!"java.lang.String".equals(qualifiedName)) {
                return;
            }
            registerMethodCallError(expression);
        }
    }
}