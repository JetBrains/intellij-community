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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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

    @NotNull
    @Override
    protected InspectionGadgetsFix[] buildFixes(Object... infos) {
        final List<InspectionGadgetsFix> result = new ArrayList();
        final PsiElement expression = (PsiElement)infos[0];
        PsiElement parent = expression.getParent();
        if (parent instanceof PsiExpression) {
            final PsiExpression binaryExpression =
                    (PsiExpression)parent;
            final PsiType type = binaryExpression.getType();
            if (type != null && type != PsiType.CHAR) {
                final String typeText = type.getCanonicalText();
                result.add(new CharUsedInArithmeticContentCastFix(typeText));
            }
        }
        if (!(expression instanceof PsiLiteralExpression)) {
            return result.toArray(new InspectionGadgetsFix[result.size()]);
        }
        while (parent instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) parent;
            if (TypeUtils.expressionHasType("java.lang.String",
                    binaryExpression)) {
                result.add(new CharUsedInArithmeticContentFix());
                break;
            }
            parent = parent.getParent();
        }

        return result.toArray(new InspectionGadgetsFix[result.size()]);
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

    private static class CharUsedInArithmeticContentCastFix
            extends InspectionGadgetsFix {

        private final String typeText;

        CharUsedInArithmeticContentCastFix(String typeText) {
            this.typeText = typeText;
        }

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "char.used.in.arithmetic.context.cast.quickfix", typeText);
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiExpression)) {
                return;
            }
            final PsiExpression expression = (PsiExpression)element;
            final String expressionText = expression.getText();
            replaceExpression(expression,
                    '(' + typeText + ')' + expressionText);
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