package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.IsConstantExpressionVisitor;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import com.siyeh.ig.psiutils.ComparisonUtils;

public class IncompatibleMaskInspection extends ExpressionInspection {


    public String getDisplayName() {
        return "Incompatible bitwise mask operation";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) location;
        final PsiJavaToken operationSign = binaryExpression.getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
        if (tokenType.equals(JavaTokenType.EQEQ)) {
            return "#ref is always false #loc";
        } else {
            return "#ref is always true #loc";
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new IncompatibleMaskVisitor(this, inspectionManager, onTheFly);
    }


    private static class IncompatibleMaskVisitor extends BaseInspectionVisitor {
        private IncompatibleMaskVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            if(!ComparisonUtils.isEqualityComparison(expression)){
                return;
            }
            final PsiType expressionType = expression.getType();
            if (expressionType == null) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            final PsiExpression strippedRhs = stripExpression(rhs);
            if (strippedRhs == null) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression strippedLhs = stripExpression(lhs);
            if (strippedLhs == null) {
                return;
            }
            if (isConstantMask(strippedLhs) && isConstant(strippedRhs)) {
                if (isIncompatibleMask((PsiBinaryExpression) strippedLhs, strippedRhs)) {
                    registerError(expression);
                }
            } else if (isConstantMask(strippedRhs) && isConstant(strippedLhs)) {
                if (isIncompatibleMask((PsiBinaryExpression) strippedRhs, strippedLhs)) {
                    registerError(expression);
                }
            }
        }

    }

    private static PsiExpression stripExpression(PsiExpression exp) {
        if (exp == null) {
            return null;
        }
        if (exp instanceof PsiParenthesizedExpression) {
            final PsiExpression body =
                    ((PsiParenthesizedExpression) exp).getExpression();
            return stripExpression(body);
        }
        return exp;
    }

    private static boolean isIncompatibleMask(PsiBinaryExpression maskExpression, PsiExpression constantExpression) {
        final PsiJavaToken sign = maskExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        final Object constantValue =
                ConstantExpressionUtil.computeCastTo(constantExpression, PsiType.LONG);
        if (constantValue == null) {
            return false;
        }
        final long constantLongValue = ((Long) constantValue).longValue();

        final long constantMaskValue;
        final PsiExpression maskRhs = maskExpression.getROperand();
        final PsiExpression maskLhs = maskExpression.getLOperand();
        if (isConstant(maskRhs)) {
            final Object rhsValue =
                    ConstantExpressionUtil.computeCastTo(maskRhs, PsiType.LONG);
            constantMaskValue = ((Long) rhsValue).longValue();
        } else {
            final Object lhsValue =
                    ConstantExpressionUtil.computeCastTo(maskLhs, PsiType.LONG);
            constantMaskValue = ((Long) lhsValue).longValue();
        }

        if (tokenType.equals(JavaTokenType.OR)) {
            if ((constantMaskValue | constantLongValue) != constantLongValue) {
                return true;
            }
        }
        if (tokenType.equals(JavaTokenType.AND)) {
            if ((constantMaskValue | constantLongValue) != constantMaskValue) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConstant(PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        final IsConstantExpressionVisitor visitor =
                new IsConstantExpressionVisitor();
        expression.accept(visitor);
        if (!visitor.isConstant()) {
            return false;
        }
        return true;
    }

    private static boolean isConstantMask(PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        if (!(expression instanceof PsiBinaryExpression)) {
            return false;
        }

        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        if (sign == null) {
            return false;
        }
        final IElementType tokenType = sign.getTokenType();
        if (!tokenType.equals(JavaTokenType.OR) && !tokenType.equals(JavaTokenType.AND)) {
            return false;
        }
        final PsiExpression rhs = binaryExpression.getROperand();
        if (isConstant(rhs)) {
            return true;
        }
        final PsiExpression lhs = binaryExpression.getLOperand();
        if (isConstant(lhs)) {
            return true;
        }
        return false;
    }

}
