package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.WellFormednessUtils;

public class ChainedEqualityInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Chained equality comparisons";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Chained equality comparison #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ChainedEqualityVisitor(this, inspectionManager, onTheFly);
    }

    private static class ChainedEqualityVisitor extends BaseInspectionVisitor {
        private ChainedEqualityVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            if (!isEqualityComparison(expression)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            if (!(lhs instanceof PsiBinaryExpression)) {
                return;
            }
            if (!isEqualityComparison((PsiBinaryExpression) lhs)) {
                return;
            }
            registerError(expression);
        }

        private static boolean isEqualityComparison(PsiBinaryExpression expression) {
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            return tokenType.equals(JavaTokenType.EQEQ) ||
                    tokenType.equals(JavaTokenType.NE);
        }

    }

}
