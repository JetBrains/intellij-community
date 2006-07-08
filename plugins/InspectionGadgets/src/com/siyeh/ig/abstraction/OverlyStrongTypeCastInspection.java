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
package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jetbrains.annotations.NotNull;

public class OverlyStrongTypeCastInspection extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "overly.strong.type.cast.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        final PsiType expectedType = (PsiType)infos[0];
        final String typeText = expectedType.getPresentableText();
        return InspectionGadgetsBundle.message(
                "overly.strong.type.cast.problem.descriptor", typeText);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new OverlyStrongCastFix();
    }

    private static class OverlyStrongCastFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "overly.strong.type.cast.weaken.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement castTypeElement = descriptor.getPsiElement();
            final PsiTypeCastExpression expression =
                    (PsiTypeCastExpression) castTypeElement.getParent();
            if (expression == null) {
                return;
            }
            final PsiType expectedType =
                    ExpectedTypeUtils.findExpectedType(expression, true);
            if (expectedType == null) {
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if (operand == null) {
                return;
            }
            final String newExpression =
                    '(' + expectedType.getCanonicalText() + ')' +
                    operand.getText();
            replaceExpressionAndShorten(expression, newExpression);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new OverlyStrongTypeCastVisitor();
    }

    private static class OverlyStrongTypeCastVisitor
            extends BaseInspectionVisitor {

        public void visitTypeCastExpression(
                @NotNull PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);
            final PsiExpression operand = expression.getOperand();
            if (operand == null) {
                return;
            }
            final PsiType operandType = operand.getType();
            if (operandType == null) {
                return;
            }
            final PsiType type = expression.getType();
            if (type == null) {
                return;
            }
            final PsiType expectedType =
                    ExpectedTypeUtils.findExpectedType(expression, true);
            if (expectedType == null) {
                return;
            }
            if (expectedType.equals(type)) {
                return;
            }
            final PsiClass resolved = PsiUtil.resolveClassInType(expectedType);
            if (resolved != null && !resolved.isPhysical()) {
                return;
            }
            if (expectedType.isAssignableFrom(operandType)) {
                //then it's redundant, and caught by the built-in exception
                return;
            }
            if (isTypeParameter(expectedType)) {
                return;
            }
            if (expectedType instanceof PsiArrayType) {
                final PsiArrayType arrayType = (PsiArrayType) expectedType;
                final PsiType componentType = arrayType.getDeepComponentType();
                if(isTypeParameter(componentType)) {
                    return;
                }
            }
            if (type instanceof PsiPrimitiveType ||
                    expectedType instanceof PsiPrimitiveType) {
                return;
            }
            if (PsiPrimitiveType.getUnboxedType(type) != null ||
                    PsiPrimitiveType.getUnboxedType(expectedType) != null) {
                return;
            }
            if (expectedType instanceof PsiClassType) {
                final PsiClassType expectedClassType =
                        (PsiClassType)expectedType;
                final PsiClassType rawType = expectedClassType.rawType();
                if (type.equals(rawType)) {
                    return;
                }
            }
            final PsiTypeElement castTypeElement = expression.getCastType();
            if (castTypeElement == null) {
                return;
            }
            registerError(castTypeElement, expectedType);
        }

        private static boolean isTypeParameter(PsiType type){
            if(!(type instanceof PsiClassType)){
                return false;
            }
            final PsiClassType classType = (PsiClassType)type;
            final PsiClass aClass = classType.resolve();
            if(aClass == null){
                return false;
            }
            return aClass instanceof PsiTypeParameter;
        }
    }
}