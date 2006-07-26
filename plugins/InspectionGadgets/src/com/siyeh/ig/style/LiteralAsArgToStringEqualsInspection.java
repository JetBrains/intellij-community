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
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LiteralAsArgToStringEqualsInspection
        extends ExpressionInspection {

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final String methodName = (String)infos[0];
        return InspectionGadgetsBundle.message(
                "literal.as.arg.to.string.equals.problem.descriptor",
                methodName);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LiteralAsArgToEqualsVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new SwapEqualsFix();
    }

    private static class SwapEqualsFix extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "literal.as.arg.to.string.equals.flip.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiExpression argument =
                    (PsiExpression)descriptor.getPsiElement();
            final PsiElement argumentList = argument.getParent();
            final PsiMethodCallExpression expression =
                    (PsiMethodCallExpression)argumentList.getParent();
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final PsiExpression target =
                    methodExpression.getQualifierExpression();
            final String methodName = methodExpression.getReferenceName();
            final PsiExpression strippedTarget =
                    ParenthesesUtils.stripParentheses(target);
            if (strippedTarget == null) {
                return;
            }
            final PsiExpression strippedArg =
                    ParenthesesUtils.stripParentheses(argument);
            if (strippedArg == null) {
                return;
            }
            final String callString;
            if (ParenthesesUtils.getPrecedence(strippedArg) >
                    ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
                callString = '(' + strippedArg.getText() + ")." + methodName +
                        '(' + strippedTarget.getText() + ')';
            } else {
                callString = strippedArg.getText() + '.' + methodName + '(' +
                        strippedTarget.getText() + ')';
            }
            replaceExpression(expression, callString);
        }
    }

    private static class LiteralAsArgToEqualsVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.EQUALS.equals(methodName) &&
                    !HardcodedMethodConstants.EQUALS_IGNORE_CASE.equals(
                            methodName)) {
                return;
            }
            final PsiExpressionList argList = expression.getArgumentList();
            final PsiExpression[] args = argList.getExpressions();
            if (args.length != 1) {
                return;
            }
            final PsiExpression argument = args[0];
            final PsiType argumentType = argument.getType();
            if (argumentType == null) {
                return;
            }
            if (!(argument instanceof PsiLiteralExpression)) {
                return;
            }
            if (!TypeUtils.isJavaLangString(argumentType)) {
                return;
            }
            final PsiExpression target =
                    methodExpression.getQualifierExpression();
            if (target instanceof PsiLiteralExpression) {
                return;
            }
            registerError(argument, methodName);
        }
    }
}