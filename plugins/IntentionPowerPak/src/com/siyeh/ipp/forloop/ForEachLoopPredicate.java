package com.siyeh.ipp.forloop;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;

class ForEachLoopPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiJavaToken)){
            return false;
        }
        final PsiJavaToken token = (PsiJavaToken) element;
        final IElementType tokenType = token.getTokenType();
        if(!JavaTokenType.FOR_KEYWORD.equals(tokenType)){
            return false;
        }
        final PsiElement parent = element.getParent();
        return parent instanceof PsiForeachStatement;
    }
}
