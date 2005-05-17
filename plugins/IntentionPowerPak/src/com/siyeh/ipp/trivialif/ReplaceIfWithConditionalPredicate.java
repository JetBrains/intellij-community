package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ConditionalUtils;
import com.siyeh.ipp.psiutils.EquivalenceChecker;
import com.siyeh.ipp.psiutils.ErrorUtil;

class ReplaceIfWithConditionalPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiJavaToken)){
            return false;
        }
        final PsiJavaToken token = (PsiJavaToken) element;

        final PsiElement parent = token.getParent();
        if(!(parent instanceof PsiIfStatement)){
            return false;
        }
        final PsiIfStatement ifStatement = (PsiIfStatement) parent;

        if(ErrorUtil.containsError(ifStatement)){
            return false;
        }
        final PsiExpression condition = ifStatement.getCondition();
        if(condition == null || !condition.isValid()){
            return false;
        }
        if(isReplaceableAssignment(ifStatement)){
            return true;
        }
        if(isReplaceableReturn(ifStatement)){
            return true;
        }
        return isReplaceableImplicitReturn(ifStatement);
    }

    public static boolean isReplaceableImplicitReturn(PsiIfStatement ifStatement){
        final PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsForward(ifStatement,
                                                new Class[]{
                                                    PsiWhiteSpace.class});
        if(!(nextStatement instanceof PsiReturnStatement)){
            return false;
        }
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        if(!(thenBranch instanceof PsiReturnStatement)){
            return false;
        }

        final PsiExpression thenReturn =
                ((PsiReturnStatement) thenBranch).getReturnValue();
        if(thenReturn == null){
            return false;
        }
        final PsiType thenType = thenReturn.getType();
        if(thenType == null){
            return false;
        }

        final PsiExpression elseReturn =
                ((PsiReturnStatement) nextStatement).getReturnValue();
        if(elseReturn == null){
            return false;
        }
        final PsiType elseType = elseReturn.getType();
        if(elseType == null){
            return false;
        }

        return thenType.isAssignableFrom(elseType) ||
                elseType.isAssignableFrom(thenType);
    }

    public static boolean isReplaceableReturn(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(!(thenBranch instanceof PsiReturnStatement) ||
                !(elseBranch instanceof PsiReturnStatement)){
            return false;
        }
        final PsiExpression thenReturn =
                ((PsiReturnStatement) thenBranch).getReturnValue();
        if(thenReturn == null){
            return false;
        }
        final PsiExpression elseReturn =
                ((PsiReturnStatement) elseBranch).getReturnValue();
        if(elseReturn == null){
            return false;
        }
        final PsiType thenType = thenReturn.getType();
        final PsiType elseType = elseReturn.getType();
        if(thenType == null || elseType == null){
            return false;
        }
        return thenType.isAssignableFrom(elseType) ||
                elseType.isAssignableFrom(thenType);
    }

    public static boolean isReplaceableAssignment(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        if(thenBranch == null){
            return false;
        }
        thenBranch = ConditionalUtils.stripBraces(thenBranch);

        if(!ConditionalUtils.isAssignment(thenBranch)){
            return false;
        }
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(elseBranch == null){
            return false;
        }
        if(!ConditionalUtils.isAssignment(elseBranch)){
            return false;
        }
        final PsiAssignmentExpression thenExpression =
                (PsiAssignmentExpression) ((PsiExpressionStatement) thenBranch).getExpression();
        final PsiAssignmentExpression elseExpression =
                (PsiAssignmentExpression) ((PsiExpressionStatement) elseBranch).getExpression();
        final PsiJavaToken thenSign = thenExpression.getOperationSign();
        final PsiJavaToken elseSign = elseExpression.getOperationSign();
        if(!thenSign.getTokenType().equals(elseSign.getTokenType())){
            return false;
        }
        final PsiExpression thenLhs = thenExpression.getLExpression();
        if(thenExpression.getRExpression() == null || thenLhs == null){
            return false;
        }
        if(elseExpression.getRExpression() == null ||
                elseExpression.getLExpression() == null){
            return false;
        }
        final PsiExpression thenRhs = thenExpression.getRExpression();
        final PsiType thenType = thenRhs.getType();
        if(thenType == null){
            return false;
        }
        final PsiExpression elseRhs = elseExpression.getRExpression();
        final PsiType elseType = elseRhs.getType();
        if(elseType == null){
            return false;
        }
        if(!(thenType.isAssignableFrom(elseType) ||
                elseType.isAssignableFrom(thenType))){
            return false;
        }
        final PsiExpression elseLhs = elseExpression.getLExpression();
        return EquivalenceChecker.expressionsAreEquivalent(thenLhs, elseLhs);
    }
}
