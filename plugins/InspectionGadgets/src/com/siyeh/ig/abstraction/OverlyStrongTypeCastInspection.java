package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;

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

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiElement castTypeElement = descriptor.getPsiElement();
            final PsiTypeCastExpression expression =
                    (PsiTypeCastExpression) castTypeElement.getParent();
            final PsiType expectedType =
                    ExpectedTypeUtils.findExpectedType(expression);
            final String newExpression =
                    '(' + expectedType.getPresentableText() + ')' +
                    expression.getOperand().getText();
            replaceExpression(project, expression, newExpression);
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new OverlyStrongTypeCastVisitor(this, inspectionManager, onTheFly);
    }

    private static class OverlyStrongTypeCastVisitor extends BaseInspectionVisitor {
        private OverlyStrongTypeCastVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitTypeCastExpression(PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);
            final PsiExpression operand = expression.getOperand();
            if (operand == null) {
                return;
            }
            final PsiType operandType = operand.getType();
            if (operandType == null) {
                return;
            }
            final PsiTypeElement typeElement = expression.getCastType();
            if (typeElement == null) {
                return;
            }
            final PsiType type = typeElement.getType();
            if (type == null) {
                return;
            }
            final PsiType expectedType =
                    ExpectedTypeUtils.findExpectedType(expression);
            if (expectedType == null) {
                return;
            }
            if (expectedType.equals(type)) {
                return;
            }
            if (expectedType.getCanonicalText().equals("_Dummy_.__Array__")) {
                return;
            }
            if (expectedType.isAssignableFrom(operandType)) {
                return;     //then it's redundant, and caught by the built-in exception
            }
            if(expectedType instanceof PsiClassType)
            {
                final PsiClass aClass = ((PsiClassType) expectedType).resolve();
                if(aClass == null)
                {
                    return;
                }
                if(aClass.getContext() instanceof PsiTypeParameterList)
                {
                    return;
                }
            }
            if (ClassUtils.isPrimitiveNumericType(type) ||
                    ClassUtils.isPrimitiveNumericType(expectedType)) {
                return;
            }
            if (TypeConversionUtil.isPrimitiveWrapper(type.getCanonicalText()) ||
                    TypeConversionUtil.isPrimitiveWrapper(type.getCanonicalText())) {
                return;
            }
            registerError(typeElement);
        }
    }
}
