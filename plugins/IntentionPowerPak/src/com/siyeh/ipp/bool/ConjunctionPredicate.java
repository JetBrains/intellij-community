package com.siyeh.ipp.bool;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;

class ConjunctionPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression expression = (PsiBinaryExpression) element;
        final PsiJavaToken sign = expression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        return tokenType.equals(JavaTokenType.ANDAND) ||
                       tokenType.equals(JavaTokenType.OROR);
    }
}
