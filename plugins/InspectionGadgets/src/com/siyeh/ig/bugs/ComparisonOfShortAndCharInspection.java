package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.TypeUtils;

public class ComparisonOfShortAndCharInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Comparison of short and char values";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Equality comparison (#ref) of short and char values #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ComparisonOfShortAndCharVisitor(this, inspectionManager, onTheFly);
    }

    private static class ComparisonOfShortAndCharVisitor extends BaseInspectionVisitor {
        private static final String SHORT = "short";
        private static final String CHAR = "char";

        private ComparisonOfShortAndCharVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if (!ComparisonUtils.isEqualityComparison(expression)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression rhs = expression.getROperand();
            if (TypeUtils.expressionHasType(SHORT, lhs) && TypeUtils.expressionHasType(CHAR, rhs)) {
                registerError(expression);
            } else if (TypeUtils.expressionHasType(CHAR, lhs) && TypeUtils.expressionHasType(SHORT, rhs)) {
                registerError(expression);
            }
        }
    }

}
