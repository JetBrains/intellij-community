package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;

public class MultiplyOrDivideByPowerOfTwoInspection extends ExpressionInspection {
    private final MultiplyByPowerOfTwoFix fix = new MultiplyByPowerOfTwoFix();

    public String getDisplayName() {
        return "Multiply or divide by power of two";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref can be replaced with " +
                calculateReplacementShift((PsiExpression) location) + " #loc";
    }

    private static String calculateReplacementShift(PsiExpression expression) {
        final PsiExpression lhs;
        final PsiExpression rhs;
        final String operator;
        if (expression instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression exp = (PsiAssignmentExpression) expression;

            final PsiJavaToken sign = exp.getOperationSign();
            lhs = exp.getLExpression();
            rhs = exp.getRExpression();
            final IElementType tokenType = sign.getTokenType();
            if (tokenType.equals(JavaTokenType.ASTERISKEQ)) {
                operator = "<<=";
            } else {
                operator = ">>=";
            }
        } else {
            final PsiBinaryExpression exp = (PsiBinaryExpression) expression;
            final PsiJavaToken sign = exp.getOperationSign();
            lhs = exp.getLOperand();
            rhs = exp.getROperand();
            final IElementType tokenType = sign.getTokenType();
            if (tokenType.equals(JavaTokenType.ASTERISK)) {
                operator = "<<";
            } else {
                operator = ">>";
            }
        }
        final String lhsText;
        if(ParenthesesUtils.getPrecendence(lhs) >
                ParenthesesUtils.SHIFT_PRECEDENCE){
            lhsText = '(' + lhs.getText() + ')';
        } else{
            lhsText = lhs.getText();
        }
        String expString =
        lhsText + operator + ShiftUtils.getLogBaseTwo(rhs);
        final PsiElement parent = expression.getParent();
        if(parent != null && parent instanceof PsiExpression){
            if(!(parent instanceof PsiParenthesizedExpression) &&
                    ParenthesesUtils.getPrecendence((PsiExpression) parent) <
                            ParenthesesUtils.SHIFT_PRECEDENCE){
                expString = '(' + expString + ')';
            }
        }
        return expString;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ConstantShiftVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class MultiplyByPowerOfTwoFix extends InspectionGadgetsFix {
        public String getName() {
            return "Replace with shift";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiExpression expression = (PsiExpression) descriptor.getPsiElement();
            final String newExpression = calculateReplacementShift(expression);
            replaceExpression(project, expression, newExpression);
        }

    }

    private static class ConstantShiftVisitor extends BaseInspectionVisitor {
        private ConstantShiftVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression))
            {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();

            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.ASTERISK) &&
                    !tokenType.equals(JavaTokenType.DIV)) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            if (!ShiftUtils.isPowerOfTwo(rhs)) {
                return;
            }
            final PsiType type = expression.getType();
            if (type == null) {
                return;
            }
            if (!ClassUtils.isIntegral(type)) {
                return;
            }
            registerError(expression);
        }

        public void visitAssignmentExpression(PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.ASTERISKEQ) &&
                    !tokenType.equals(JavaTokenType.DIVEQ)) {
                return;
            }
            final PsiExpression rhs = expression.getRExpression();
            if (!ShiftUtils.isPowerOfTwo(rhs)) {
                return;
            }

            final PsiType type = expression.getType();
            if (type == null) {
                return;
            }
            if (!ClassUtils.isIntegral(type)) {
                return;
            }
            registerError(expression);
        }
    }

}
