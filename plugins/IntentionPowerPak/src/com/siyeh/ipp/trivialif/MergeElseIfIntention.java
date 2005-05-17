package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;

public class MergeElseIfIntention extends Intention{
    public String getText(){
        return "Merge else-if";
    }

    public String getFamilyName(){
        return "Merge Else If";
    }

    public PsiElementPredicate getElementPredicate(){
        return new MergeElseIfPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiJavaToken token = (PsiJavaToken) element;
        final PsiIfStatement parentStatement =
                (PsiIfStatement) token.getParent();
        final PsiBlockStatement elseBranch =
                (PsiBlockStatement) parentStatement.getElseBranch();
        final PsiCodeBlock elseBranchBlock = elseBranch.getCodeBlock();
        final PsiStatement elseBranchContents =
                elseBranchBlock.getStatements()[0];
        replaceStatement(elseBranchContents.getText(), elseBranch);
    }
}