package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class AssignmentToNullInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Assignment to 'null'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Assignment of variable #ref to null #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new AssignmentToNullVisitor(this, inspectionManager, onTheFly);
    }

    private static class AssignmentToNullVisitor extends BaseInspectionVisitor {
        private AssignmentToNullVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitLiteralExpression(PsiLiteralExpression value) {
            super.visitLiteralExpression(value);
            final String text = value.getText();
            if (!"null".equals(text)) {
                return;
            }
            PsiElement parent = value.getParent();
            while (parent != null &&
                    (parent instanceof PsiParenthesizedExpression ||
                    parent instanceof PsiConditionalExpression ||
                    parent instanceof PsiTypeCastExpression)) {
                parent = parent.getParent();
            }
            if (parent == null || !(parent instanceof PsiAssignmentExpression)) {
                return;
            }
            final PsiExpression lhs = ((PsiAssignmentExpression) parent).getLExpression();
            registerError(lhs);
        }

    }

}
