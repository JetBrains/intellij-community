/*
 * Copyright 2011 Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class MathRandomCastToIntInspection extends BaseInspection {
    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "math.random.cast.to.int.display.name");
    }

    @NotNull
    @Override
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "math.random.cast.to.int.problem.descriptor");
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final PsiTypeCastExpression expression =
                (PsiTypeCastExpression) infos[0];
        final PsiElement parent = expression.getParent();
        if (!(parent instanceof PsiBinaryExpression)) {
            return null;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) parent;
        final IElementType tokenType = binaryExpression.getOperationTokenType();
        if (JavaTokenType.ASTERISK != tokenType) {
            return null;
        }
        return new MathRandomCastToIntegerFix();
    }

    private static class MathRandomCastToIntegerFix
            extends InspectionGadgetsFix {
        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "math.random.cast.to.int.quickfix");
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiTypeCastExpression)) {
                return;
            }
            final PsiTypeCastExpression typeCastExpression =
                    (PsiTypeCastExpression) parent;
            final PsiElement grandParent = typeCastExpression.getParent();
            if (!(grandParent instanceof PsiBinaryExpression)) {
                return;
            }
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) grandParent;
            final PsiExpression operand = typeCastExpression.getOperand();
            if (operand == null) {
                return;
            }
            @NonNls final StringBuilder newExpression = new StringBuilder();
            newExpression.append("(int)(");
            final PsiExpression lhs = binaryExpression.getLOperand();
            if (typeCastExpression.equals(lhs)) {
                newExpression.append(operand.getText());
            } else {
                newExpression.append(lhs.getText());
            }
            newExpression.append(binaryExpression.getOperationSign().getText());
            final PsiExpression rhs = binaryExpression.getROperand();
            if (rhs == null) {
                return;
            }
            if (typeCastExpression.equals(rhs)) {
                newExpression.append(operand.getText());
            } else {
                newExpression.append(rhs.getText());
            }
            newExpression.append(')');
            replaceExpression(binaryExpression, newExpression.toString());
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new MathRandomCastToIntegerVisitor();
    }

    private static class MathRandomCastToIntegerVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitTypeCastExpression(PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);
            final PsiExpression operand = expression.getOperand();
            if (!(operand instanceof PsiMethodCallExpression)) {
                return;
            }
            final PsiTypeElement castType = expression.getCastType();
            if (castType == null) {
                return;
            }
            final PsiType type = castType.getType();
            if (!PsiType.INT.equals(type)) {
                return;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) operand;
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            @NonNls
            final String referenceName = methodExpression.getReferenceName();
            if (!"random".equals(referenceName)) {
                return;
            }
            final PsiMethod method = methodCallExpression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            final String qualifiedName = containingClass.getQualifiedName();
            if (!"java.lang.Math".equals(qualifiedName)) {
                return;
            }
            registerError(methodCallExpression, expression);
        }
    }
}
