package com.siyeh.ipp.trivialif;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class SplitElseIfIntention extends Intention{
    public String getText(){
        return "Split else-if";
    }

    public String getFamilyName(){
        return "Split Else If";
    }

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new SplitElseIfPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiJavaToken token = (PsiJavaToken) element;
        final PsiIfStatement parentStatement =
                (PsiIfStatement) token.getParent();
        assert parentStatement != null;
        final PsiStatement elseBranch = parentStatement.getElseBranch();
        final String newStatement = '{' + elseBranch.getText() + '}';
        replaceStatement(newStatement, elseBranch);
    }
}