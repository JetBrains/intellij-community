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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class StringEqualityInspection extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "string.comparison.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "string.comparison.problem.descriptor");
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ObjectEqualityVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new EqualityToEqualsFix();
    }

    private static class EqualityToEqualsFix extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "string.comparison.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement comparisonToken = descriptor.getPsiElement();
            final PsiBinaryExpression expression =
                    (PsiBinaryExpression) comparisonToken.getParent();
            if (expression == null) {
                return;
            }
            boolean negated=false;
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (tokenType.equals(JavaTokenType.NE)) {
                negated = true;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression strippedLhs =
                    ParenthesesUtils.stripParentheses(lhs);
            if (strippedLhs == null) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            final PsiExpression strippedRhs =
                    ParenthesesUtils.stripParentheses(rhs);
            if (strippedRhs == null) {
                return;
            }
            @NonNls final String expString;
            if (ParenthesesUtils.getPrecedence(strippedLhs) >
                ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
                expString = '(' + strippedLhs.getText() + ").equals(" +
                            strippedRhs.getText() + ')';
            } else {
                expString = strippedLhs.getText() + ".equals(" +
                            strippedRhs.getText() + ')';
            }
            final String newExpression;
            if (negated) {
                newExpression = '!' + expString;
            } else {
                newExpression = expString;
            }
            replaceExpression(expression, newExpression);
        }

    }

    private static class ObjectEqualityVisitor extends BaseInspectionVisitor {

        public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null)){
                return;
            }
            if (!ComparisonUtils.isEqualityComparison(expression)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            if (!isStringType(lhs)) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            if (!isStringType(rhs)) {
                return;
            }
            final String lhsText = lhs.getText();
            if (PsiKeyword.NULL.equals(lhsText)) {
                return;
            }
            if (rhs == null) {
                return;
            }
            final String rhsText = rhs.getText();
            if (PsiKeyword.NULL.equals(rhsText)) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            registerError(sign);
        }

        private static boolean isStringType(PsiExpression lhs) {
            if (lhs == null) {
                return false;
            }
            final PsiType lhsType = lhs.getType();
            if (lhsType == null) {
                return false;
            }
            return TypeUtils.isJavaLangString(lhsType);
        }
    }
}