/*
 * Copyright 2005 Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuspiciousSystemArraycopyInspection extends StatementInspection {

    private String errorString = InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor");

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("suspicious.system.arraycopy.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    @Nullable
    protected String buildErrorString(PsiElement location) {
        return errorString;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SuspiciousSystemArraycopyVisitor();
    }

    // todo: move buildErrorString methods to BaseInspectionVisitor
    // will make creating error string much easier
    // no more parameters needed
    // will be abstract so impossible to forget to implement
    private class SuspiciousSystemArraycopyVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String name = methodExpression.getReferenceName();
            if (!"arraycopy".equals(name)) {
                return;
            }
            final PsiExpression qualifierExpression =
                    methodExpression.getQualifierExpression();
            if (!(qualifierExpression instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) qualifierExpression;
            final String canonicalText = referenceExpression.getCanonicalText();
            if (!canonicalText.equals("java.lang.System")) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 5) {
                return;
            }
            final PsiExpression src = arguments[0];
            final PsiType srcType = src.getType();
            final PsiExpression srcPos = arguments[1];
            if (isNegativeArgument(srcPos)) {
                errorString = InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor1");
                registerError(srcPos);
            }
            final PsiExpression destPos = arguments[3];
            if (isNegativeArgument(destPos)) {
                errorString = InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor2");
                registerError(destPos);
            }
            final PsiExpression length = arguments[4];
            if (isNegativeArgument(length)) {
                errorString = InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor3");
                registerError(length);
            }
            boolean notArrayReported = false;
            if (!(srcType instanceof PsiArrayType)) {
                errorString = InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor4");
                registerError(src);
                notArrayReported = true;
            }
            final PsiExpression dest = arguments[2];
            final PsiType destType = dest.getType();
            if (!(destType instanceof PsiArrayType)) {
                errorString = InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor5");
                registerError(dest);
                notArrayReported = true;
            }
            if (notArrayReported) {
                return;
            }
            if (!srcType.equals(destType)) {
                errorString = InspectionGadgetsBundle.message("suspicious.system.arraycopy.problem.descriptor6");
                registerError(dest);
            }
        }

        private boolean isNegativeArgument(@NotNull PsiExpression argument) {
            final PsiManager manager = argument.getManager();
            final PsiConstantEvaluationHelper constantEvaluationHelper =
                    manager.getConstantEvaluationHelper();
            final Object constant =
                    constantEvaluationHelper.computeConstantExpression(argument);
            if (!(constant instanceof Integer)) {
                return false;
            }
            final Integer integer = (Integer)constant;
            return integer.intValue() < 0;
        }
    }
}