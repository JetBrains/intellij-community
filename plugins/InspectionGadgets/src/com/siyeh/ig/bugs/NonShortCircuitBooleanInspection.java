package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.project.Project;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.WellFormednessUtils;

public class NonShortCircuitBooleanInspection extends ExpressionInspection {
    private final InspectionGadgetsFix fix = new NonShortCircuitBooleanFix();

    public String getDisplayName() {
        return "Non-short-circuit boolean expression";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Non-short-circuit boolean expression #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class NonShortCircuitBooleanFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace with short circuit expression";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiBinaryExpression expression = (PsiBinaryExpression) descriptor.getPsiElement();
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression rhs = expression.getROperand();
            final PsiJavaToken operationSign = expression.getOperationSign();
            final IElementType tokenType = operationSign.getTokenType();
            final String newExpression = lhs.getText() + getShortCircuitOperand(tokenType) + rhs.getText();
            replaceExpression(project, expression, newExpression);
        }

        private static String getShortCircuitOperand(IElementType tokenType) {
            if (tokenType.equals(JavaTokenType.AND)) {
                return "&&";
            }
            else {
                return "||";
            }
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new NonShortCircuitBooleanVisitor(this, inspectionManager, onTheFly);
    }

    private static class NonShortCircuitBooleanVisitor extends BaseInspectionVisitor {
        private NonShortCircuitBooleanVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }

            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.AND) &&
                    !tokenType.equals(JavaTokenType.OR)) {
                return;
            }
            final PsiType type = expression.getType();
            if (type == null) {
                return;
            }
            if (!type.equals(PsiType.BOOLEAN)) {
                return;
            }
            registerError(expression);
        }
    }
}
