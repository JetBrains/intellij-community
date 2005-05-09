package com.siyeh.ipp.switchtoif;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class SwitchPredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiJavaToken)){
            return false;
        }
        final PsiJavaToken token = (PsiJavaToken) element;
        final IElementType tokenType = token.getTokenType();
        if(!JavaTokenType.SWITCH_KEYWORD.equals(tokenType)){
            return false;
        }
        final PsiElement parent = element.getParent();
        if(!(parent instanceof PsiSwitchStatement))
        {
            return false;
        }
        final PsiSwitchStatement switchStatement = (PsiSwitchStatement) parent;
        if(ErrorUtil.containsError(switchStatement))
        {
            return false;
        }
        final PsiExpression expression = switchStatement.getExpression();
        if(expression == null|| !expression.isValid())
        {
            return false;
        }
        final PsiCodeBlock body = switchStatement.getBody();
        return body != null;
    }

}
