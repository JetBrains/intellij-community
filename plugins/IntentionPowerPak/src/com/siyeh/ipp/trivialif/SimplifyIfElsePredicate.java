package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ConditionalUtils;
import com.siyeh.ipp.psiutils.EquivalenceChecker;

class SimplifyIfElsePredicate implements PsiElementPredicate{
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
        if(isSimplifiableAssignment(ifStatement)){
            return true;
        }

        if(isSimplifiableReturn(ifStatement)){
            return true;
        }

        if(isSimplifiableImplicitReturn(ifStatement)){
            return true;
        }
        if(isSimplifiableAssignmentNegated(ifStatement)){
            return true;
        }

        if(isSimplifiableReturnNegated(ifStatement)){
            return true;
        }

        if(isSimplifiableImplicitReturnNegated(ifStatement)){
            return true;
        }
        if(isSimplifiableImplicitAssignment(ifStatement)){
            return true;
        }

        if(isSimplifiableImplicitAssignmentNegated(ifStatement)){
            return true;
        }
        return false;
    }

    public static boolean isSimplifiableImplicitReturn(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        final PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsForward(ifStatement,
                                                new Class[]{PsiWhiteSpace.class});
        if(!(nextStatement instanceof PsiStatement)){
            return false;
        }
        final PsiStatement elseBranch = (PsiStatement) nextStatement;
        if(ConditionalUtils.isReturn(thenBranch, "true")
                   && ConditionalUtils.isReturn(elseBranch, "false")){
            return true;
        }
        return false;
    }

    public static boolean isSimplifiableImplicitReturnNegated(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);

        final PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsForward(ifStatement,
                                                new Class[]{PsiWhiteSpace.class});
        if(!(nextStatement instanceof PsiStatement)){
            return false;
        }
        final PsiStatement elseBranch = (PsiStatement) nextStatement;
        if(ConditionalUtils.isReturn(thenBranch, "false")
                   && ConditionalUtils.isReturn(elseBranch, "true")){
            return true;
        }
        return false;
    }

    public static boolean isSimplifiableReturn(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(ConditionalUtils.isReturn(thenBranch, "true")
                   && ConditionalUtils.isReturn(elseBranch, "false")){
            return true;
        }
        return false;
    }

    public static boolean isSimplifiableReturnNegated(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(ConditionalUtils.isReturn(thenBranch, "false")
                   && ConditionalUtils.isReturn(elseBranch, "true")){
            return true;
        }
        return false;
    }

    public static boolean isSimplifiableAssignment(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(ConditionalUtils.isAssignment(thenBranch, "true") &&
                   ConditionalUtils.isAssignment(elseBranch, "false")){
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
            final PsiExpression elseLhs = elseExpression.getLExpression();
            return EquivalenceChecker.expressionsAreEquivalent(thenLhs,
                                                               elseLhs);
        } else{
            return false;
        }
    }

    public static boolean isSimplifiableAssignmentNegated(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(ConditionalUtils.isAssignment(thenBranch, "false") &&
                   ConditionalUtils.isAssignment(elseBranch, "true")){
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
            final PsiExpression elseLhs = elseExpression.getLExpression();
            return EquivalenceChecker.expressionsAreEquivalent(thenLhs,
                                                               elseLhs);
        } else{
            return false;
        }
    }

    public static boolean isSimplifiableImplicitAssignment(PsiIfStatement ifStatement){
        if(ifStatement.getElseBranch() != null){
            return false;
        }
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        final PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsBackward(ifStatement,
                                                 new Class[]{PsiWhiteSpace.class});
        if(!(nextStatement instanceof PsiStatement)){
            return false;
        }
        PsiStatement elseBranch = (PsiStatement) nextStatement;

        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(ConditionalUtils.isAssignment(thenBranch, "true") &&
                   ConditionalUtils.isAssignment(elseBranch, "false")){
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
            final PsiExpression elseLhs = elseExpression.getLExpression();
            return EquivalenceChecker.expressionsAreEquivalent(thenLhs,
                                                               elseLhs);
        } else{
            return false;
        }
    }

    public static boolean isSimplifiableImplicitAssignmentNegated(PsiIfStatement ifStatement){
        if(ifStatement.getElseBranch() != null){
            return false;
        }
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        final PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsBackward(ifStatement,
                                                 new Class[]{PsiWhiteSpace.class});
        if(!(nextStatement instanceof PsiStatement)){
            return false;
        }
        PsiStatement elseBranch = (PsiStatement) nextStatement;

        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(ConditionalUtils.isAssignment(thenBranch, "false") &&
                   ConditionalUtils.isAssignment(elseBranch, "true")){
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
            final PsiExpression elseLhs = elseExpression.getLExpression();
            return EquivalenceChecker.expressionsAreEquivalent(thenLhs,
                                                               elseLhs);
        } else{
            return false;
        }
    }
}
