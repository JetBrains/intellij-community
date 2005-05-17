package com.siyeh.ig.confusing;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class IncrementDecrementUsedAsExpressionInspection extends ExpressionInspection {
    public String getID(){
        return "ValueOfIncrementOrDecrementUsed";
    }
    public String getDisplayName() {
        return "Value of ++ or -- used";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final String expressionType;
        if (location instanceof PsiPostfixExpression) {
            final PsiJavaToken sign = ((PsiPostfixExpression) location).getOperationSign();
            if (sign.getTokenType().equals(JavaTokenType.PLUSPLUS)) {
                expressionType = "post-increment";
            } else {
                expressionType = "post-decrement";
            }
        } else {
            final PsiJavaToken sign = ((PsiPrefixExpression) location).getOperationSign();
            if (sign.getTokenType().equals(JavaTokenType.PLUSPLUS)) {
                expressionType = "pre-increment";
            } else {
                expressionType = "pre-decrement";
            }
        }
        return "Value of " + expressionType + " expression #ref is used #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new IncrementDecrementUsedAsExpressionVisitor();
    }

    private static class IncrementDecrementUsedAsExpressionVisitor extends BaseInspectionVisitor {


        public void visitPostfixExpression(@NotNull PsiPostfixExpression expression) {
            super.visitPostfixExpression(expression);
            if(expression.getParent() instanceof PsiExpressionStatement ||
                                    (expression.getParent() instanceof PsiExpressionList &&
                                        expression.getParent().getParent() instanceof PsiExpressionListStatement)) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            if (sign == null) {
                return;
            }
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                    !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            registerError(expression);
        }

        public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);

            if (expression.getParent() instanceof PsiExpressionStatement ||
                            (expression.getParent() instanceof PsiExpressionList &&
                                    expression.getParent()
                                    .getParent() instanceof PsiExpressionListStatement)) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            if (sign == null) {
                return;
            }
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                    !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            registerError(expression);
        }
    }

}
