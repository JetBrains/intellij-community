package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.ExtractMethodFix;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;

import javax.swing.*;

public class OverlyComplexBooleanExpressionInspection extends StatementInspection {
    private static final int TERM_LIMIT = 3;

    public int m_limit = TERM_LIMIT;  //this is public for the DefaultJDOMExternalizer thingy

    private InspectionGadgetsFix fix = new ExtractMethodFix();

    public String getDisplayName() {
        return "Overly complex boolean expression";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    private int getLimit() {
        return m_limit;
    }

    public JComponent createOptionsPanel() {
        return new SingleIntegerFieldOptionsPanel("Maximum number of terms:",
                this, "m_limit");
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    protected String buildErrorString(PsiElement location) {
        return "Overly complex boolean expression #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SwitchStatementWithTooManyBranchesVisitor(this, inspectionManager, onTheFly);
    }

    private class SwitchStatementWithTooManyBranchesVisitor extends BaseInspectionVisitor {
        private SwitchStatementWithTooManyBranchesVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            checkExpression(expression);
        }

        public void visitPrefixExpression(PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            checkExpression(expression);
        }

        public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
            super.visitParenthesizedExpression(expression);
            checkExpression(expression);
        }

        private void checkExpression(PsiExpression expression) {
            if (!isBoolean(expression)) {
                return;
            }
            if (isParentBoolean(expression)) {
                return;
            }
            final int numTerms = countTerms(expression);
            if (numTerms <= getLimit()) {
                return;
            }
            registerError(expression);
        }

        private int countTerms(PsiExpression expression) {
            if (!isBoolean(expression)) {
                return 1;
            }
            if (expression instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
                final PsiExpression lhs = binaryExpression.getLOperand();
                final PsiExpression rhs = binaryExpression.getROperand();
                return countTerms(lhs) + countTerms(rhs);
            } else if (expression instanceof PsiPrefixExpression) {
                final PsiPrefixExpression prefixExpression = (PsiPrefixExpression) expression;
                final PsiExpression operand = prefixExpression.getOperand();
                return countTerms(operand);
            } else if (expression instanceof PsiParenthesizedExpression) {
                final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) expression;
                final PsiExpression contents = parenthesizedExpression.getExpression();
                return countTerms(contents);
            }
            return 1;
        }

        private boolean isParentBoolean(PsiExpression expression) {
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiExpression)) {
                return false;
            }
            return isBoolean((PsiExpression) parent);
        }

        private boolean isBoolean(PsiExpression expression) {
            if (expression instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
                final PsiJavaToken sign = binaryExpression.getOperationSign();
                final IElementType tokenType = sign.getTokenType();
                return tokenType.equals(JavaTokenType.ANDAND) ||
                        tokenType.equals(JavaTokenType.OROR);
            } else if (expression instanceof PsiPrefixExpression) {
                final PsiPrefixExpression prefixExpression = (PsiPrefixExpression) expression;
                final PsiJavaToken sign = prefixExpression.getOperationSign();
                final IElementType tokenType = sign.getTokenType();
                return tokenType.equals(JavaTokenType.EXCL);
            } else if (expression instanceof PsiParenthesizedExpression) {
                final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) expression;
                final PsiExpression contents = parenthesizedExpression.getExpression();
                return isBoolean(contents);
            }
            return false;
        }
    }
}
