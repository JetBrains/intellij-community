package com.siyeh.ipp.shift;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;

public class ReplaceShiftWithMultiplyIntention extends MutablyNamedIntention{
    protected String getTextForElement(PsiElement element){
        if(element instanceof PsiBinaryExpression){
            final PsiBinaryExpression exp = (PsiBinaryExpression) element;
            final PsiJavaToken sign = exp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final String operatorString;
            if(tokenType.equals(JavaTokenType.LTLT)){
                operatorString = "*";
            } else{
                operatorString = "/";
            }
            return "Replace " + sign.getText() + " with " + operatorString;
        } else{
            final PsiAssignmentExpression exp =
                    (PsiAssignmentExpression) element;
            final PsiJavaToken sign = exp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final String assignString;
            if(JavaTokenType.LTLTEQ.equals(tokenType)){
                assignString = "*=";
            } else{
                assignString = "/=";
            }
            return "Replace " + sign.getText() + " with " + assignString;
        }
    }

    public String getFamilyName(){
        return "Replace Shift with Multiply";
    }

    public PsiElementPredicate getElementPredicate(){
        return new ShiftByLiteralPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        if(element instanceof PsiBinaryExpression){
            replaceShiftWithMultiplyOrDivide(element);
        } else{
            replaceShiftAssignWithMultiplyOrDivideAssign(element);
        }
    }

    private void replaceShiftAssignWithMultiplyOrDivideAssign(PsiElement element)
            throws IncorrectOperationException{
        final PsiAssignmentExpression exp =
                (PsiAssignmentExpression) element;
        final PsiExpression lhs = exp.getLExpression();
        final PsiExpression rhs = exp.getRExpression();
        final PsiJavaToken sign = exp.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        final String assignString;
        if(tokenType.equals(JavaTokenType.LTLTEQ)){
            assignString = "*=";
        } else{
            assignString = "/=";
        }
        final String expString =
                lhs.getText() + assignString + ShiftUtils.getExpBase2(rhs);
        replaceExpression(expString, exp);
    }

    private void replaceShiftWithMultiplyOrDivide(PsiElement element)
            throws IncorrectOperationException{
        final PsiBinaryExpression exp =
                (PsiBinaryExpression) element;
        final PsiExpression lhs = exp.getLOperand();
        final PsiExpression rhs = exp.getROperand();
        final PsiJavaToken sign = exp.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        final String operatorString;
        if(tokenType.equals(JavaTokenType.LTLT)){
            operatorString = "*";
        } else{
            operatorString = "/";
        }
        final String lhsText;
        if(ParenthesesUtils.getPrecendence(lhs) >
                ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE){
            lhsText = '(' + lhs.getText() + ')';
        } else{
            lhsText = lhs.getText();
        }
        String expString =
                lhsText + operatorString + ShiftUtils.getExpBase2(rhs);
        final PsiElement parent = exp.getParent();
        if(parent != null && parent instanceof PsiExpression){
            if(!(parent instanceof PsiParenthesizedExpression) &&
                    ParenthesesUtils.getPrecendence((PsiExpression) parent) <
                            ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE){
                expString = '(' + expString + ')';
            }
        }
        replaceExpression(expString, exp);
    }
}
