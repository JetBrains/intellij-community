package com.siyeh.ipp.parenthesis;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class RemoveUnnecessaryParenthesesIntention extends Intention{
    public String getText(){
        return "Remove unnecessary parentheses";
    }

    public String getFamilyName(){
        return "Remove Unnecessary Parentheses";
    }

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new UnnecessaryParenthesesPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        PsiExpression exp = (PsiExpression) element;
        while(exp.getParent() instanceof PsiExpression){
            exp = (PsiExpression) exp.getParent();
            assert exp != null;
        }
        final String newExpression = ParenthesesUtils.removeParentheses(exp);
        replaceExpression(newExpression, exp);
    }
}
