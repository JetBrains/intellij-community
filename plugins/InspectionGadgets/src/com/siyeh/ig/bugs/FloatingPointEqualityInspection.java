package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;

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

    public BaseInspectionVisitor buildVisitor() {
        return new FloatingPointEqualityComparisonVisitor();
    }

    private static class FloatingPointEqualityComparisonVisitor extends BaseInspectionVisitor {

        public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            if(!ComparisonUtils.isEqualityComparison(expression))
            {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression rhs = expression.getROperand();
            if(!isFloatingPointType(lhs) && !isFloatingPointType(rhs)){
                return;
            }
            registerError(expression);
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
            return PsiType.DOUBLE.equals(type) || PsiType.FLOAT.equals(type);

        }
    }

}
