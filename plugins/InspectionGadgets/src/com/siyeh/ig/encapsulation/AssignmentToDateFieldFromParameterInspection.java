package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.TypeUtils;

public class AssignmentToDateFieldFromParameterInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Assignment to Date or Calendar field from parameter";
    }

    public String getGroupDisplayName() {
        return GroupNames.ENCAPSULATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiAssignmentExpression assignment = (PsiAssignmentExpression) location.getParent();
        final PsiExpression lhs = assignment.getLExpression();
        final PsiExpression rhs = assignment.getRExpression();
        final PsiElement element = ((PsiReference) lhs).resolve();

        final PsiField field = (PsiField) element;
        final PsiType type = field.getType();
        return "assignment to " + type.getPresentableText() + " field #ref from parameter " + rhs.getText() + "#loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new AssignmentToDateFieldFromParameterVisitor(this, inspectionManager, onTheFly);
    }

    private static class AssignmentToDateFieldFromParameterVisitor extends BaseInspectionVisitor {
        private AssignmentToDateFieldFromParameterVisitor(BaseInspection inspection,
                                                                InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitAssignmentExpression(PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            if (sign == null) {
                return;
            }
            if (!sign.getTokenType().equals(JavaTokenType.EQ)) {
                return;
            }
            final PsiExpression lhs = expression.getLExpression();
            if (lhs == null) {
                return;
            }
            if (!TypeUtils.expressionHasTypeOrSubtype("java.util.Date", lhs)
                    && !TypeUtils.expressionHasTypeOrSubtype("java.util.Calendar", lhs)) {
                return;
            }
            final PsiExpression rhs = expression.getRExpression();
            if (rhs == null) {
                return;
            }
            if (!(rhs instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiElement element = ((PsiReference) rhs).resolve();
            if (!(element instanceof PsiParameter)) {
                return;
            }
            if (!(element.getParent() instanceof PsiParameterList)) {
                return;
            }
            registerError(lhs);
        }
    }

}
