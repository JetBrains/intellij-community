package com.siyeh.ipp.trivialif;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiStatement;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.base.PsiElementPredicate;

class SplitElseIfPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiJavaToken)){
            return false;
        }
        final String text = element.getText();
        if(!"else".equals(text)){
            return false;
        }
        final PsiJavaToken token = (PsiJavaToken) element;

        final PsiElement parent = token.getParent();
        if(!(parent instanceof PsiIfStatement)){
            return false;
        }
        final PsiIfStatement ifStatement = (PsiIfStatement) parent;
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        final PsiStatement elseBranch = ifStatement.getElseBranch();
        if(thenBranch == null){
            return false;
        }
        if(elseBranch == null){
            return false;
        }
        if(!(elseBranch instanceof PsiIfStatement)){
            return false;
        }

        return true;
    }
}
