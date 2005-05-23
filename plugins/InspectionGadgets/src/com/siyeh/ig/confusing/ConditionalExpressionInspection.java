package com.siyeh.ig.confusing;

import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class ConditionalExpressionInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Conditional expression (?:)";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Conditional expression #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConditionalExpressionVisitor();
    }

    private static class ConditionalExpressionVisitor extends BaseInspectionVisitor {

        public void visitConditionalExpression(PsiConditionalExpression exp) {
            super.visitConditionalExpression(exp);
            registerError(exp);
        }

    }

}
