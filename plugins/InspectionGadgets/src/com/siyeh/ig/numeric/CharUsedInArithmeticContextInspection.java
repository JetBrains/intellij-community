/*
 * Copyright 2008 Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;import com.siyeh.InspectionGadgetsBundle;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class CharUsedInArithmeticContextInspection extends BaseInspection {
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "char.used.in.arithmetic.context.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "char.used.in.arithmetic.context.problem.descriptor");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final PsiExpression expression = (PsiExpression) infos[0];
        if (!(expression instanceof PsiLiteralExpression)) {
            return null;
        }
        PsiElement parent = expression.getParent();
        while (parent instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) parent;
            if (TypeUtils.expressionHasType("java.lang.String",
                    binaryExpression)) {
                return new CharUsedInArithmeticContentFix();
            }
            parent = parent.getParent();
        }
        return null;
    }

    private static class CharUsedInArithmeticContentFix
            extends InspectionGadgetsFix {
        
        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "char.used.in.arithmetic.context.quickfix");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiLiteralExpression)) {
                return;
            }
            final PsiLiteralExpression literalExpression =
                    (PsiLiteralExpression) element;
            final Object literal = literalExpression.getValue();
            if (!(literal instanceof Character)) {
                return;
            }
            replaceExpression(literalExpression, "\"" + literal + '"');
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CharUsedInArithmeticContextVisitor();
    }

    private static class CharUsedInArithmeticContextVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            //final IElementType tokenType = expression.getOperationTokenType();
            //if (!JavaTokenType.PLUS.equals(tokenType) &&
            //        !JavaTokenType.MINUS.equals(tokenType)) {
            //    return;
            //}
            final PsiExpression lhs = expression.getLOperand();
            final PsiType lhsType = lhs.getType();
            if (PsiType.CHAR.equals(lhsType)) {
                registerError(lhs, lhs);
            }
            final PsiExpression rhs = expression.getROperand();
            if (rhs == null) {
                return;
            }
            final PsiType rhsType = rhs.getType();
            if (PsiType.CHAR.equals(rhsType)) {
                registerError(rhs, rhs);
            }
        }
    }
}