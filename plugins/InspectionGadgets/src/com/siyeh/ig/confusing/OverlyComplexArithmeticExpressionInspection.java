package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.ExtractMethodFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;

import javax.swing.*;

public class OverlyComplexArithmeticExpressionInspection extends StatementInspection {
    private static final int TERM_LIMIT = 6;

    /** @noinspection PublicField*/
    public int m_limit = TERM_LIMIT;  //this is public for the DefaultJDOMExternalizer thingy

    private final InspectionGadgetsFix fix = new ExtractMethodFix();

    public String getDisplayName() {
        return "Overly complex arithmetic expression";
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

    protected String buildErrorString(PsiElement location) {
        return "Overly complex arithmetic expression #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
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
            if (!isArithmetic(expression)) {
                return;
            }
            if (isParentArithmetic(expression)) {
                return;
            }
            final int numTerms = countTerms(expression);
            if (numTerms <= getLimit()) {
                return;
            }
            registerError(expression);
        }

        private int countTerms(PsiExpression expression) {
            if (!isArithmetic(expression)) {
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

        private boolean isParentArithmetic(PsiExpression expression) {
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiExpression)) {
                return false;
            }
            return isArithmetic((PsiExpression) parent);
        }

        private boolean isArithmetic(PsiExpression expression) {
            if (expression instanceof PsiBinaryExpression) {
                final PsiType type = expression.getType();
                if (TypeUtils.isJavaLangString(type)) {
                    return false; //ignore string concatenations
                }
                final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
                final PsiJavaToken sign = binaryExpression.getOperationSign();
                final IElementType tokenType = sign.getTokenType();
                return tokenType.equals(JavaTokenType.PLUS) ||
                        tokenType.equals(JavaTokenType.MINUS) ||
                        tokenType.equals(JavaTokenType.ASTERISK) ||
                        tokenType.equals(JavaTokenType.DIV) ||
                        tokenType.equals(JavaTokenType.PERC);
            } else if (expression instanceof PsiPrefixExpression) {
                final PsiPrefixExpression prefixExpression = (PsiPrefixExpression) expression;
                final PsiJavaToken sign = prefixExpression.getOperationSign();
                final IElementType tokenType = sign.getTokenType();
                return tokenType.equals(JavaTokenType.PLUS) ||
                        tokenType.equals(JavaTokenType.MINUS);
            } else if (expression instanceof PsiParenthesizedExpression) {
                final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) expression;
                final PsiExpression contents = parenthesizedExpression.getExpression();
                return isArithmetic(contents);
            }
            return false;
        }
    }
}
