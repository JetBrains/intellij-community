package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ControlFlowUtils;
import com.siyeh.ipp.psiutils.EquivalenceChecker;

class MergeIfOrPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        return isMergableExplicitIf(element) || isMergableImplicitIf(element);
    }

    public static boolean isMergableExplicitIf(PsiElement element){
        if(!(element instanceof PsiJavaToken)){
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
        final PsiIfStatement childIfStatement = (PsiIfStatement) elseBranch;

        final PsiStatement childThenBranch = childIfStatement.getThenBranch();
        return EquivalenceChecker.statementsAreEquivalent(thenBranch,
                                                          childThenBranch);
    }

    private static boolean isMergableImplicitIf(PsiElement element){
        if(!(element instanceof PsiJavaToken)){
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
        if(elseBranch != null){
            return false;
        }

        if(ControlFlowUtils.statementMayCompleteNormally(thenBranch)){
            return false;
        }
        final PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsForward(ifStatement,
                                                new Class[]{PsiWhiteSpace.class});
        if(!(nextStatement instanceof PsiIfStatement)){
            return false;
        }
        final PsiIfStatement childIfStatement = (PsiIfStatement) nextStatement;
        final PsiStatement childThenBranch = childIfStatement.getThenBranch();
        return EquivalenceChecker.statementsAreEquivalent(thenBranch,
                                                          childThenBranch);
    }
}
