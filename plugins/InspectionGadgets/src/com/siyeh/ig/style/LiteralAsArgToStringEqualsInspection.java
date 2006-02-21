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

    private final SwapEqualsFix swapEqualsFix = new SwapEqualsFix();

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiMethodCallExpression expression =
                (PsiMethodCallExpression)location;
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        return InspectionGadgetsBundle.message(
                "literal.as.arg.to.string.equals.problem.descriptor",
                methodName);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LiteralAsArgToEqualsVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return swapEqualsFix;
    }

    private static class SwapEqualsFix extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "literal.as.arg.to.string.equals.flip.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiMethodCallExpression expression =
                    (PsiMethodCallExpression)descriptor.getPsiElement();
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final PsiExpression target =
                    methodExpression.getQualifierExpression();
            final String methodName = methodExpression.getReferenceName();
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression arg = argumentList.getExpressions()[0];
            final PsiExpression strippedTarget =
                    ParenthesesUtils.stripParentheses(target);
            if (strippedTarget == null) {
                return;
            }
            final PsiExpression strippedArg =
                    ParenthesesUtils.stripParentheses(arg);
            if (strippedArg == null) {
                return;
            }
            final String callString;
            if (ParenthesesUtils.getPrecendence(strippedArg) >
                    ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
                callString = '(' + strippedArg.getText() + ")." + methodName +
                        '(' + strippedTarget.getText() + ')';
            }
            else {
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
                    !"equalsIgnoreCase".equals(methodName)) {
                return;
            }
            final PsiExpressionList argList = expression.getArgumentList();
            final PsiExpression[] args = argList.getExpressions();
            if (args.length != 1) {
                return;
            }
            final PsiExpression arg = args[0];
            final PsiType argType = arg.getType();
            if (argType == null) {
                return;
            }
            if (!(arg instanceof PsiLiteralExpression)) {
                return;
            }
            if (!TypeUtils.isJavaLangString(argType)) {
                return;
            }
            final PsiExpression target =
                    methodExpression.getQualifierExpression();
            if (target instanceof PsiLiteralExpression) {
                return;
            }
            registerError(expression);
        }
    }
}