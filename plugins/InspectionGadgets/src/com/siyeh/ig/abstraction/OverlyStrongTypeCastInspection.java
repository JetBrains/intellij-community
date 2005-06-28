package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jetbrains.annotations.NotNull;

public class OverlyStrongTypeCastInspection extends ExpressionInspection {

    private final OverlyStrongCastFix fix = new OverlyStrongCastFix();

    public String getDisplayName() {
        return "Overly-strong type cast";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Cast to #ref can be weakened #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class OverlyStrongCastFix extends InspectionGadgetsFix {
        public String getName() {
            return "Weaken overly-strong cast";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement castTypeElement = descriptor.getPsiElement();
            final PsiTypeCastExpression expression =
                    (PsiTypeCastExpression) castTypeElement.getParent();
            final PsiType expectedType =
                    ExpectedTypeUtils.findExpectedType(expression, true);
            assert expression != null;
            final PsiExpression operand = expression.getOperand();
            final String newExpression =
                    '(' + expectedType.getPresentableText() + ')' +
                    operand.getText();
            replaceExpression(expression, newExpression);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new OverlyStrongTypeCastVisitor();
    }

    private static class OverlyStrongTypeCastVisitor extends BaseInspectionVisitor {

        public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
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
            final String expectedTypeText = expectedType.getCanonicalText();
            if ("_Dummy_.__Array__".equals(expectedTypeText)) {
                return;
            }
            if (expectedType.isAssignableFrom(operandType)) {
                return;     //then it's redundant, and caught by the built-in exception
            }
            if(isTypeParameter(expectedType))
            {
                return;
            }
            if(expectedType instanceof PsiArrayType)
            {
                final PsiArrayType arrayType = (PsiArrayType) expectedType;
                final PsiType componentType = arrayType.getDeepComponentType();
                if(isTypeParameter(componentType))
                {
                    return;
                }
            }
            if (ClassUtils.isPrimitiveNumericType(type) ||
                    ClassUtils.isPrimitiveNumericType(expectedType)) {
                return;
            }
            final String typeText = type.getCanonicalText();
            if (TypeConversionUtil.isPrimitiveWrapper(typeText) ||
                    TypeConversionUtil.isPrimitiveWrapper(expectedTypeText)) {
                return;
            }
            final PsiTypeElement castTypeElement = expression.getCastType();
            registerError(castTypeElement);
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
