package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class ConditionalExpressionInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Conditional expression (?:)";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Conditional expression #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ConditionalExpressionVisitor(this, inspectionManager, onTheFly);
    }

    private static class ConditionalExpressionVisitor extends BaseInspectionVisitor {
        private ConditionalExpressionVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitConditionalExpression(PsiConditionalExpression exp) {
            super.visitConditionalExpression(exp);
            registerError(exp);
        }

    }

}
