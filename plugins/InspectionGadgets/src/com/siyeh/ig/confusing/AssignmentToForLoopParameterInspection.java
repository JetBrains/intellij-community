package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class AssignmentToForLoopParameterInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Assignment to for-loop parameter";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Assignment to for-loop parameter #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new AssignmentToForLoopParameterVisitor(this, inspectionManager, onTheFly);
    }

    private static class AssignmentToForLoopParameterVisitor extends BaseInspectionVisitor {
        private AssignmentToForLoopParameterVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitAssignmentExpression(PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            final PsiExpression lhs = expression.getLExpression();
            if (lhs == null) {
                return;
            }
            checkForForLoopParam(lhs);
            checkForForeachLoopParam(lhs);
        }

        public void visitPrefixExpression(PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            if (sign == null) {
                return;
            }
            if (sign.getTokenType() != JavaTokenType.PLUSPLUS &&
                    sign.getTokenType() != JavaTokenType.MINUSMINUS) {
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if (operand == null) {
                return;
            }
            checkForForLoopParam(operand);
        }

        public void visitPostfixExpression(PsiPostfixExpression expression) {
            super.visitPostfixExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            if (sign == null) {
                return;
            }
            if (sign.getTokenType() != JavaTokenType.PLUSPLUS &&
                    sign.getTokenType() != JavaTokenType.MINUSMINUS) {
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if (operand == null) {
                return;
            }
            checkForForLoopParam(operand);
        }

        private void checkForForLoopParam(PsiExpression expression) {
            if (!(expression instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression ref = (PsiReferenceExpression) expression;
            final PsiElement element = ref.resolve();
            if (!(element instanceof PsiLocalVariable)) {
                return;
            }
            final PsiLocalVariable variable = (PsiLocalVariable) element;
            final PsiDeclarationStatement decl = (PsiDeclarationStatement) variable.getParent();
            if (!(decl.getParent() instanceof PsiForStatement)) {
                return;
            }
            final PsiForStatement forStatement = (PsiForStatement) decl.getParent();
            final PsiStatement initialization = forStatement.getInitialization();
            if (initialization == null) {
                return;
            }
            if (!initialization.equals(decl)) {
                return;
            }
            if (!isInForStatementBody(expression, forStatement)) {
                return;
            }
            registerError(expression);
        }

        private void checkForForeachLoopParam(PsiExpression expression) {
            if (!(expression instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression ref = (PsiReferenceExpression) expression;
            final PsiElement element = ref.resolve();
            if (!(element instanceof PsiParameter)) {
                return;
            }
            final PsiParameter parameter = (PsiParameter) element;
            if (!(parameter.getParent() instanceof PsiForeachStatement)) {
                return;
            }
            registerError(expression);
        }

        private static boolean isInForStatementBody(PsiExpression expression, PsiForStatement statement) {
            final PsiStatement body = statement.getBody();
            return PsiTreeUtil.isAncestor(body, expression, true);
        }
    }

}
