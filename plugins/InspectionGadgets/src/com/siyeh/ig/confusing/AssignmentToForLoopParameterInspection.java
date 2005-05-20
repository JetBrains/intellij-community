package com.siyeh.ig.confusing;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;                    
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;

public class AssignmentToForLoopParameterInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Assignment to 'for' loop parameter";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Assignment to for-loop parameter #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AssignmentToForLoopParameterVisitor();
    }

    private static class AssignmentToForLoopParameterVisitor extends BaseInspectionVisitor {

        public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiExpression lhs = expression.getLExpression();
            checkForForLoopParam(lhs);
            checkForForeachLoopParam(lhs);
        }

        public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
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
            checkForForLoopParam(operand);
        }

        public void visitPostfixExpression(@NotNull PsiPostfixExpression expression) {
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
