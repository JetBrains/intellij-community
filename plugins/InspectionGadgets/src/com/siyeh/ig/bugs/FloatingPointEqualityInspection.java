package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class FloatingPointEqualityInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Floating point equality comparison";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref: floating point values compared for exact equality #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new FloatingPointEqualityComparisonVisitor(this, inspectionManager, onTheFly);
    }

    private static class FloatingPointEqualityComparisonVisitor extends BaseInspectionVisitor {
        private FloatingPointEqualityComparisonVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            if (sign == null) {
                return;
            }
            final IElementType tokenType = sign.getTokenType();
            if (!(tokenType.equals(JavaTokenType.EQEQ) ||
                    tokenType.equals(JavaTokenType.NE))) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            if (isFloatingPointType(lhs)) {
                registerError(expression);
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            if (isFloatingPointType(rhs)) {
                registerError(expression);
                return;
            }
        }

        private static boolean isFloatingPointType(PsiExpression expression) {
            if(expression == null)
            {
                return false;
            }
            final PsiType type = expression.getType();
            if(type== null)
            {
                return false;
            }
            return TypeConversionUtil.isDoubleType(type) ||
                    TypeConversionUtil.isFloatType(type);

        }
    }

}
