package com.siyeh.ipp.initialization;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.siyeh.ipp.base.PsiElementPredicate;

public class SplitDeclarationAndInitializationPredicate
        implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiField)){
            return false;
        }
        final PsiField field = (PsiField) element;
        final PsiExpression initializer = field.getInitializer();
        if(initializer == null){
            return false;
        }
        return true;
    }
}
