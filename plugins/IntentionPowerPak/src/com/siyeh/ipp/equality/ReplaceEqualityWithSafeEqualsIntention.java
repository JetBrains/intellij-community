package com.siyeh.ipp.equality;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class ReplaceEqualityWithSafeEqualsIntention extends Intention{
    public String getText(){
        return "Replace == with safe .equals()";
    }

    public String getFamilyName(){
        return "Replace Equality With Safe Equals";
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
        final String lhsText = strippedLhs.getText();
        final String rhsText = strippedRhs.getText();
        final String expString;
        if(ParenthesesUtils.getPrecendence(strippedLhs) >
                ParenthesesUtils.METHOD_CALL_PRECEDENCE){
            expString = lhsText + "==null?" + rhsText + " == null:(" + lhsText +
                    ").equals(" + rhsText + ')';
        } else{
            expString = lhsText + "==null?" + rhsText + " == null:" + lhsText +
                    ".equals(" + rhsText + ')';
        }
        replaceExpression(expString, exp);
    }
}
