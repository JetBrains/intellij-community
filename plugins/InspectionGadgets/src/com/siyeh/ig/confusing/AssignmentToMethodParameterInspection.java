package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.WellFormednessUtils;

public class AssignmentToMethodParameterInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Assignment to method parameter";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Assignment to method parameter #ref #loc ";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new AssignmentToMethodParameterVisitor(this, inspectionManager, onTheFly);
    }

    private static class AssignmentToMethodParameterVisitor extends BaseInspectionVisitor {
        private AssignmentToMethodParameterVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitAssignmentExpression(PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiExpression lhs = expression.getLExpression();
            checkForMethodParam(lhs);
        }

        public void visitPrefixExpression(PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            if (sign == null) {
                return;
            }
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                    !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if (operand == null) {
                return;
            }
            checkForMethodParam(operand);
        }

        public void visitPostfixExpression(PsiPostfixExpression expression) {
            super.visitPostfixExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            if (sign == null) {
                return;
            }
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                    !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if (operand == null) {
                return;
            }
            checkForMethodParam(operand);
        }

        private void checkForMethodParam(PsiExpression expression) {
            if (!(expression instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression ref = (PsiReferenceExpression) expression;
            final PsiElement variable = ref.resolve();
            if (!(variable instanceof PsiParameter)) {
                return;
            }
            if (((PsiParameter)variable).getDeclarationScope() instanceof PsiCatchSection) {
                return;
            }
            registerError(expression);
        }
    }

}
