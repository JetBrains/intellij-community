package com.siyeh.ig.internationalization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

import java.util.HashSet;
import java.util.Set;

public class CharacterComparisonInspection extends ExpressionInspection {

    private static final Set s_comparisonOperators = new HashSet(4);

    static {
        s_comparisonOperators.add(">");
        s_comparisonOperators.add("<");
        s_comparisonOperators.add(">=");
        s_comparisonOperators.add("<=");
    }

    private static boolean isComparison(String operator) {
        return s_comparisonOperators.contains(operator);
    }

    public String getDisplayName() {
        return "Character comparison";
    }

    public String getGroupDisplayName() {
        return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Character comparison #ref in an internationalized context #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new CharacterComparisonVisitor(this, inspectionManager, onTheFly);
    }

    private static class CharacterComparisonVisitor extends BaseInspectionVisitor {
        private CharacterComparisonVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            if (sign == null) {
                return;
            }
            final String operand = sign.getText();
            if (!isComparison(operand)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            if (!isCharacter(lhs)) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            if (rhs == null) {
                return;
            }
            final PsiType rhsType = rhs.getType();
            if (rhsType == null) {
                return;
            }
            if (!rhsType.equals(PsiType.CHAR)) {
                return;
            }
            registerError(expression);

        }
    }

    private static boolean isCharacter(PsiExpression lhs) {

        if (lhs == null) {
            return false;
        }
        final PsiType lhsType = lhs.getType();
        if (lhsType == null) {
            return false;
        }
        final String text = lhsType.getCanonicalText();
        return "char".equals(text) ||
                "java.lang.Character".equals(text);
    }

}
