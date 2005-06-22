package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;

public class NestedConditionalExpressionInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Nested conditional expression";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Nested conditional expression #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NestedConditionalExpressionVisitor();
    }

    private static class NestedConditionalExpressionVisitor extends BaseInspectionVisitor {

        public void visitConditionalExpression(PsiConditionalExpression exp) {
            super.visitConditionalExpression(exp);
            if (PsiTreeUtil.getParentOfType(exp, PsiConditionalExpression.class) != null) {
                registerError(exp);
            }
        }

    }

}
