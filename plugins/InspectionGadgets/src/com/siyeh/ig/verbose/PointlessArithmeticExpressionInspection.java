package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;

public class PointlessArithmeticExpressionInspection extends ExpressionInspection {
    private final PointlessArithmeticFix fix = new PointlessArithmeticFix();

    public String getDisplayName() {
        return "Pointless arithmetic expression";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref can be replaced with " +
                calculateReplacementExpression((PsiExpression) location) + " #loc";
    }

    private static String calculateReplacementExpression(PsiExpression expression) {
        final PsiExpression lhs;
        final PsiExpression rhs;
        final PsiBinaryExpression exp = (PsiBinaryExpression) expression;
        final PsiJavaToken sign = exp.getOperationSign();
        lhs = exp.getLOperand();
        rhs = exp.getROperand();
        final IElementType tokenType = sign.getTokenType();
        if (tokenType.equals(JavaTokenType.PLUS)) {
            if (isZero(lhs)) {
                return rhs.getText();
            } else {
                return lhs.getText();
            }
        } else if (tokenType.equals(JavaTokenType.MINUS)) {
            return lhs.getText();
        } else if (tokenType.equals(JavaTokenType.ASTERISK)) {
            if (isOne(lhs)) {
                return rhs.getText();
            } else if (isOne(rhs)) {
                return lhs.getText();
            } else {
                return "0";
            }
        } else if (tokenType.equals(JavaTokenType.DIV)) {
            return lhs.getText();
        } else {
            return "";
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new PointlessArithmeticVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class PointlessArithmeticFix extends InspectionGadgetsFix {
        public String getName() {
            return "Simplify";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiExpression expression = (PsiExpression) descriptor.getPsiElement();
            final String newExpression = calculateReplacementExpression(expression);
            replaceExpression(project, expression, newExpression);
        }

    }

    private static class PointlessArithmeticVisitor extends BaseInspectionVisitor {
        private PointlessArithmeticVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
           //to avoid drilldown
        }

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            if (TypeUtils.expressionHasType("java.lang.String", expression)) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            if (sign == null) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            final PsiExpression lhs = expression.getLOperand();
            final IElementType tokenType = sign.getTokenType();
            final boolean isPointless;
            if (tokenType.equals(JavaTokenType.PLUS)) {
                isPointless = additionExpressionIsPointless(lhs, rhs);
            } else if (tokenType.equals(JavaTokenType.MINUS)) {
                isPointless = subtractionExpressionIsPointless(rhs);
            } else if (tokenType.equals(JavaTokenType.ASTERISK)) {
                isPointless = multiplyExpressionIsPointless(lhs, rhs);
            } else if (tokenType.equals(JavaTokenType.DIV)) {
                isPointless = divideExpressionIsPointless(rhs);
            } else {
                isPointless = false;
            }
            if (!isPointless) {
                return;
            }
            registerError(expression);
        }
    }

    private static boolean subtractionExpressionIsPointless(PsiExpression rhs) {
        return isZero(rhs);
    }

    private static boolean additionExpressionIsPointless(PsiExpression lhs, PsiExpression rhs) {
        return isZero(lhs) || isZero(rhs);
    }

    private static boolean multiplyExpressionIsPointless(PsiExpression lhs, PsiExpression rhs) {
        return isZero(lhs) || isZero(rhs) || isOne(lhs) || isOne(rhs);
    }

    private static boolean divideExpressionIsPointless(PsiExpression rhs) {
        return isOne(rhs);
    }

    private static boolean isZero(PsiExpression expression) {
        final Double value = (Double) ConstantExpressionUtil.computeCastTo(expression, PsiType.DOUBLE);
        return value != null && value.doubleValue() == 0.0;
    }

    private static boolean isOne(PsiExpression expression) {
        final Double value = (Double) ConstantExpressionUtil.computeCastTo(expression, PsiType.DOUBLE);
        return value != null && value.doubleValue() == 1.0;
    }

}
