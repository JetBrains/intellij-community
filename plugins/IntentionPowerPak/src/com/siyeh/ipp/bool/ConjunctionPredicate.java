package com.siyeh.ipp.bool;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;

class ConjunctionPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement exp){
        if(!(exp instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression expression = (PsiBinaryExpression) exp;
        final PsiJavaToken sign = expression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        return tokenType.equals(JavaTokenType.ANDAND) ||
                tokenType.equals(JavaTokenType.OROR);
    }
}
