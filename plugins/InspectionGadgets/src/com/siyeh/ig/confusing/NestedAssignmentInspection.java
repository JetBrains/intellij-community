package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class NestedAssignmentInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Nested assignment";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Nested assignment #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new NestedAssignmentVisitor(this, inspectionManager, onTheFly);
    }

    private static class NestedAssignmentVisitor extends BaseInspectionVisitor {
        private NestedAssignmentVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitAssignmentExpression(PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            if (expression.getParent() instanceof PsiExpressionStatement ||
                    expression.getParent() instanceof PsiExpressionList) {
                return;
            }
            registerError(expression);
        }
    }

}
