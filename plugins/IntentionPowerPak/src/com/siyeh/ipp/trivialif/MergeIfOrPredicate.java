package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.EquivalenceChecker;

class MergeIfOrPredicate implements PsiElementPredicate
{
    public boolean satisfiedBy(PsiElement element)
    {
        if(!(element instanceof PsiJavaToken))
        {
            return false;
        }
        final PsiJavaToken token = (PsiJavaToken) element;

        final PsiElement parent = token.getParent();
        if(!(parent instanceof PsiIfStatement))
        {
            return false;
        }
        final PsiIfStatement ifStatement = (PsiIfStatement) parent;
        final PsiStatement thenBranch = ifStatement.getThenBranch();
        final PsiStatement elseBranch = ifStatement.getElseBranch();
        if(thenBranch == null)
        {
            return false;
        }
        if(elseBranch == null)
        {
            return false;
        }
        if(!(elseBranch instanceof PsiIfStatement))
        {
            return false;
        }
        final PsiIfStatement childIfStatement = (PsiIfStatement) elseBranch;

        final PsiStatement childThenBranch = childIfStatement.getThenBranch();
        return EquivalenceChecker.statementsAreEquivalent(thenBranch, childThenBranch);
    }
}
