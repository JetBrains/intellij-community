package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class IncrementDecrementUsedAsExpressionInspection extends ExpressionInspection {

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
            if (sign.getTokenType() == JavaTokenType.PLUSPLUS) {
                expressionType = "post-increment";
            } else {
                expressionType = "post-decrement";
            }
        } else {
            final PsiJavaToken sign = ((PsiPrefixExpression) location).getOperationSign();
            if (sign.getTokenType() == JavaTokenType.PLUSPLUS) {
                expressionType = "pre-increment";
            } else {
                expressionType = "pre-decrement";
            }
        }
        return "Value of " + expressionType + " expression #ref is used #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new IncrementDecrementUsedAsExpressionVisitor(this, inspectionManager, onTheFly);
    }

    private static class IncrementDecrementUsedAsExpressionVisitor extends BaseInspectionVisitor {
        private IncrementDecrementUsedAsExpressionVisitor(BaseInspection inspection,
                                                          InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitPostfixExpression(PsiPostfixExpression expression) {
            super.visitPostfixExpression(expression);
            if (expression.getParent() instanceof PsiExpressionStatement) {
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

        public void visitPrefixExpression(PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);

            if (expression.getParent() instanceof PsiExpressionStatement ||
                    expression.getParent() instanceof PsiExpressionList) {
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
