package com.siyeh.ig.numeric;

import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class BadOddnessInspection extends ExpressionInspection {

    public String getGroupDisplayName() {
        return GroupNames.NUMERIC_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "bad.oddness.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new BadOddnessVisitor();
    }

    private static class BadOddnessVisitor extends BaseInspectionVisitor {

        public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if (expression.getROperand() == null) {
                return;
            }
            if (!ComparisonUtils.isEqualityComparison(expression)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression rhs = expression.getROperand();
            if (isModTwo(lhs) && hasValue(rhs, 1)) {
                registerError(expression, expression);
            }
            if (isModTwo(rhs) && hasValue(lhs, 1)) {
                registerError(expression, expression);
            }
        }
    }

    private static boolean isModTwo(PsiExpression exp) {
        if (!(exp instanceof PsiBinaryExpression)) {
            return false;
        }
        final PsiBinaryExpression binary = (PsiBinaryExpression) exp;
        final PsiJavaToken sign = binary.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if (!JavaTokenType.PERC.equals(tokenType)) {
            return false;
        }
        final PsiExpression rhs = binary.getROperand();
        final PsiExpression lhs = binary.getLOperand();
        if (rhs == null) {
            return false;
        }
        return hasValue(rhs, 2) || hasValue(lhs, 2);
    }

    private static boolean hasValue(PsiExpression expression, int testValue) {

        final Integer value = (Integer)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.INT);
        return value != null && value == testValue;
    }
}
