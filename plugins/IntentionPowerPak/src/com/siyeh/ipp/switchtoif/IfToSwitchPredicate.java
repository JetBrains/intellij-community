package com.siyeh.ipp.switchtoif;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.base.PsiElementPredicate;

class IfToSwitchPredicate implements PsiElementPredicate
{
    public boolean satisfiedBy(PsiElement exp)
    {
        if(!(exp instanceof PsiJavaToken))
        {
            return false;
        }
        final String text = exp.getText();
        if(!"if".equals(text))
        {
            return false;
        }
        if(!(exp.getParent() instanceof PsiIfStatement))
        {
            return false;
        }
        final PsiIfStatement statement = (PsiIfStatement) exp.getParent();
        return CaseUtil.getCaseExpression(statement) != null;
    }

}
