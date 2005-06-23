package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class MergeIfOrIntention extends Intention{
    public String getText(){
        return "Merge 'if's";
    }

    public String getFamilyName(){
        return "Merge Equivalent Ifs To ORed Condition";
    }

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new MergeIfOrPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiJavaToken token = (PsiJavaToken) element;
        if(MergeIfOrPredicate.isMergableExplicitIf(token)){
            replaceMergeableExplicitIf(token);
        } else{
            replaceMergeableImplicitIf(token);
        }
    }

    private static void replaceMergeableExplicitIf(PsiJavaToken token)
            throws IncorrectOperationException{
        final PsiIfStatement parentStatement =
                (PsiIfStatement) token.getParent();
        assert parentStatement != null;
        final PsiIfStatement childStatement =
                (PsiIfStatement) parentStatement.getElseBranch();

        final PsiExpression childCondition = childStatement.getCondition();
        final String childConditionText;
        if(ParenthesesUtils.getPrecendence(childCondition)
                > ParenthesesUtils.OR_PRECEDENCE){
            childConditionText = '(' + childCondition.getText() + ')';
        } else{
            childConditionText = childCondition.getText();
        }

        final PsiExpression condition = parentStatement.getCondition();
        final String parentConditionText;
        if(ParenthesesUtils.getPrecendence(condition)
                > ParenthesesUtils.OR_PRECEDENCE){
            parentConditionText = '(' + condition.getText() + ')';
        } else{
            parentConditionText = condition.getText();
        }

        final PsiStatement parentThenBranch = parentStatement.getThenBranch();
        final String parentThenBranchText = parentThenBranch.getText();
        final StringBuffer statement = new StringBuffer(
                "if(" + parentConditionText + "||" +
                        childConditionText + ')' +
                        parentThenBranchText);
        final PsiStatement childElseBranch = childStatement.getElseBranch();
        if(childElseBranch != null){
            final String childElseBranchText = childElseBranch.getText();
            statement.append("else " + childElseBranchText);
        }
        final String newStatement = statement.toString();
        replaceStatement(newStatement, parentStatement);
    }

    private static void replaceMergeableImplicitIf(PsiJavaToken token)
            throws IncorrectOperationException{
        final PsiIfStatement parentStatement =
                (PsiIfStatement) token.getParent();
        final PsiIfStatement childStatement =
                (PsiIfStatement) PsiTreeUtil.skipSiblingsForward(parentStatement,
                                                                 new Class[]{
                                                                     PsiWhiteSpace.class});

        assert childStatement != null;
        final PsiExpression childCondition = childStatement.getCondition();
        final String childConditionText;
        if(ParenthesesUtils.getPrecendence(childCondition)
                > ParenthesesUtils.OR_PRECEDENCE){
            childConditionText = '(' + childCondition.getText() + ')';
        } else{
            childConditionText = childCondition.getText();
        }

        assert parentStatement != null;
        final PsiExpression condition = parentStatement.getCondition();
        final String parentConditionText;
        if(ParenthesesUtils.getPrecendence(condition)
                > ParenthesesUtils.OR_PRECEDENCE){
            parentConditionText = '(' + condition.getText() + ')';
        } else{
            parentConditionText = condition.getText();
        }

        final PsiStatement parentThenBranch = parentStatement.getThenBranch();
        String statement =
                "if(" + parentConditionText + "||" + childConditionText + ')' +
                        parentThenBranch.getText();
        final PsiStatement childElseBranch = childStatement.getElseBranch();
        if(childElseBranch != null){
            statement += "else " + childElseBranch.getText();
        }
        replaceStatement(statement, parentStatement);
        childStatement.delete();
    }
}