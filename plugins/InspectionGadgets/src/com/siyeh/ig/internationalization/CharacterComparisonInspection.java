package com.siyeh.ig.internationalization;

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
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;

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

        public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
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

            if (!isCharacter(rhs)) {
                return;
            }
            registerError(expression);

        }
    }

    private static boolean isCharacter(PsiExpression lhs) {
       return  TypeUtils.expressionHasType("char", lhs) ||
                       TypeUtils.expressionHasType("java.lang.Character", lhs);
    }

}
