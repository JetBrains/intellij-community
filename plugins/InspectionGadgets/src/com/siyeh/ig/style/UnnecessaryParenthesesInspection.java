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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryParenthesesInspection extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessary.parentheses.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "unnecessary.parentheses.problem.descriptor");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryParenthesesVisitor();
    }

    private static class UnnecessaryParenthesesFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "unnecessary.parentheses.remove.quickfix");
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiExpression expression =
                    (PsiExpression)descriptor.getPsiElement();
            ParenthesesUtils.removeParentheses(expression);
        }
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessaryParenthesesFix();
    }

    private static class UnnecessaryParenthesesVisitor
            extends BaseInspectionVisitor {

        @Override public void visitParenthesizedExpression(
                PsiParenthesizedExpression expression) {
            final PsiElement parent = expression.getParent();
            final PsiExpression child = expression.getExpression();
            if (child == null) {
                return;
            }
            if (!(parent instanceof PsiExpression)) {
                registerError(expression);
                return;
            }
            final int parentPrecedence =
                    ParenthesesUtils.getPrecedence((PsiExpression)parent);
            final int childPrecedence = ParenthesesUtils.getPrecedence(child);
            if (parentPrecedence > childPrecedence) {
                registerError(expression);
                return;
            }
            if (parentPrecedence == childPrecedence) {
                if (!areParenthesesNeeded(expression)) {
                    registerError(expression);
                    return;
                }
            }
            super.visitParenthesizedExpression(expression);
        }

        private static boolean areParenthesesNeeded(
                PsiParenthesizedExpression expression) {
            final PsiElement parent = expression.getParent();
            final PsiElement child = expression.getExpression();
            if (parent instanceof PsiBinaryExpression &&
                    child instanceof PsiBinaryExpression) {
                final PsiBinaryExpression parentBinaryExpression =
                        (PsiBinaryExpression)parent;
                final PsiJavaToken parentSign =
                        parentBinaryExpression.getOperationSign();
                final IElementType parentOperator =
                        parentSign.getTokenType();
                final PsiBinaryExpression childBinaryExpression =
                        (PsiBinaryExpression)child;
                final PsiJavaToken childSign =
                        childBinaryExpression.getOperationSign();
                final IElementType childOperator = childSign.getTokenType();
                if (!parentOperator.equals(childOperator)) {
                    return true;
                }
                final PsiType parentType =
                        parentBinaryExpression.getType();
                if (parentType == null) {
                    return true;
                }
                final PsiType childType = childBinaryExpression.getType();
                if (!parentType.equals(childType)) {
                    return true;
                }
                if (parentBinaryExpression.getROperand() == expression) {
                    if (!ParenthesesUtils.isCommutativeBinaryOperator(childOperator)) {
                        return true;
                    }
                }
                return false;
            }
            return false;
        }
    }


}