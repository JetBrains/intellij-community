package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class NestedConditionalExpressionInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Nested conditional expression";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Nested conditional expression #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new NestedConditionalExpressionVisitor(this, inspectionManager, onTheFly);
    }

    private static class NestedConditionalExpressionVisitor extends BaseInspectionVisitor {
        private NestedConditionalExpressionVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitConditionalExpression(PsiConditionalExpression exp) {
            super.visitConditionalExpression(exp);
            if (PsiTreeUtil.getParentOfType(exp, PsiConditionalExpression.class) != null) {
                registerError(exp);
            }
        }

    }

}
