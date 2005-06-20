package com.siyeh.ipp.equality;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class ReplaceEqualityWithEqualsIntention extends Intention{
    public String getText(){
        return "Replace == with .equals()";
    }

    public String getFamilyName(){
        return "Replace Equality With Equals";
    }

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new ObjectEqualityPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiBinaryExpression exp =
                (PsiBinaryExpression) element;
        final PsiExpression lhs = exp.getLOperand();
        final PsiExpression rhs = exp.getROperand();
        final PsiExpression strippedLhs =
                ParenthesesUtils.stripParentheses(lhs);
        final PsiExpression strippedRhs =
                ParenthesesUtils.stripParentheses(rhs);
        final PsiJavaToken operationSign = exp.getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
        final String expString;
        if(tokenType.equals(JavaTokenType.EQEQ)){
            if(ParenthesesUtils.getPrecendence(strippedLhs) >
                    ParenthesesUtils.METHOD_CALL_PRECEDENCE){
                expString = '(' + strippedLhs.getText() + ").equals(" +
                        strippedRhs.getText() + ')';
            } else{
                expString = strippedLhs.getText() + ".equals(" +
                        strippedRhs.getText() + ')';
            }
        } else{
            if(ParenthesesUtils.getPrecendence(strippedLhs) >
                    ParenthesesUtils.METHOD_CALL_PRECEDENCE){
                expString = "!(" + strippedLhs.getText() + ").equals(" +
                        strippedRhs.getText() + ')';
            } else{
                expString = '!' + strippedLhs.getText() + ".equals(" +
                        strippedRhs.getText() + ')';
            }
        }
        replaceExpression(expString, exp);
    }
}
