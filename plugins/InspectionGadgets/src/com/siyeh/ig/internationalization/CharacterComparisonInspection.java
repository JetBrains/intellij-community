package com.siyeh.ig.internationalization;

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

public class CharacterComparisonInspection extends ExpressionInspection {
    public String getID(){
        return "CharacterComparison";
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
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            if(!ComparisonUtils.isComparison(expression)){
                return;
            }
            if(ComparisonUtils.isEqualityComparison(expression)){
                return;
            }

            final PsiExpression lhs = expression.getLOperand();
            if (!isCharacter(lhs)) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();

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
