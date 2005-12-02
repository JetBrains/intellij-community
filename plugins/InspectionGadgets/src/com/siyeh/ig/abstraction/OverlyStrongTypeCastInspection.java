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
package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class OverlyStrongTypeCastInspection extends ExpressionInspection {

    private final OverlyStrongCastFix fix = new OverlyStrongCastFix();

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "overly.strong.type.cast.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

  @NotNull
  protected String buildErrorString(Object arg) {
      final PsiType expectedType = (PsiType)arg;
      assert expectedType != null;
      final String typeText = expectedType.getPresentableText();
      return InspectionGadgetsBundle.message(
              "overly.strong.type.cast.problem.descriptor", typeText);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class OverlyStrongCastFix extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "overly.strong.type.cast.weaken.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
		        throws IncorrectOperationException{
            final PsiElement castTypeElement = descriptor.getPsiElement();
            final PsiTypeCastExpression expression =
                    (PsiTypeCastExpression) castTypeElement.getParent();
            assert expression != null;
            final PsiType expectedType =
                    ExpectedTypeUtils.findExpectedType(expression, true);
            assert expectedType != null;
            final PsiExpression operand = expression.getOperand();
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
            if(isTypeParameter(expectedType)) {
                return;
            }
            if(expectedType instanceof PsiArrayType) {
                final PsiArrayType arrayType = (PsiArrayType) expectedType;
                final PsiType componentType = arrayType.getDeepComponentType();
                if(isTypeParameter(componentType)) {
                    return;
                }
            }
            if (ClassUtils.isPrimitiveNumericType(type) ||
                    ClassUtils.isPrimitiveNumericType(expectedType)) {
                return;
            }
            final String typeText = type.getCanonicalText();
            final String expectedTypeText = expectedType.getCanonicalText();
            if (TypeConversionUtil.isPrimitiveWrapper(typeText) ||
                    TypeConversionUtil.isPrimitiveWrapper(expectedTypeText)) {
                return;
            }
            final PsiTypeElement castTypeElement = expression.getCastType();
            registerError(castTypeElement, expectedType);
        }

        private static boolean isTypeParameter(PsiType type){
            if(!(type instanceof PsiClassType)){
                return false;
            }
            final PsiClass aClass = ((PsiClassType) type).resolve();
            if(aClass == null){
                return false;
            }
            return aClass instanceof PsiTypeParameter;
        }
    }
}