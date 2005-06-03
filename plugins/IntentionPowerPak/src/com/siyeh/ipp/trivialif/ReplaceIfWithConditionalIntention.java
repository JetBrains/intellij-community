package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ConditionalUtils;
import com.siyeh.ipp.psiutils.ParenthesesUtils;

public class ReplaceIfWithConditionalIntention extends Intention{
    public String getText(){
        return "Replace if-else with ?:";
    }

    public String getFamilyName(){
        return "Replace If Else With Conditional";
    }

    public PsiElementPredicate getElementPredicate(){
        return new ReplaceIfWithConditionalPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiJavaToken token = (PsiJavaToken) element;
        final PsiIfStatement ifStatement = (PsiIfStatement) token.getParent();
        if(ReplaceIfWithConditionalPredicate.isReplaceableAssignment(ifStatement)){
            final PsiExpression condition = ifStatement.getCondition();
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            final PsiExpressionStatement strippedThenBranch =
                    (PsiExpressionStatement) ConditionalUtils.stripBraces(thenBranch);
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            final PsiExpressionStatement strippedElseBranch =
                    (PsiExpressionStatement) ConditionalUtils.stripBraces(elseBranch);
            final PsiAssignmentExpression thenAssign =
                    (PsiAssignmentExpression) strippedThenBranch.getExpression();
            final PsiAssignmentExpression elseAssign =
                    (PsiAssignmentExpression) strippedElseBranch.getExpression();
            final PsiExpression lhs = thenAssign.getLExpression();
            final String lhsText = lhs.getText();
            final PsiJavaToken sign = thenAssign.getOperationSign();
            final String operator = sign.getText();
            final String thenValue;
            final PsiExpression thenRhs = thenAssign.getRExpression();
            assert thenRhs != null;
            if(ParenthesesUtils.getPrecendence(thenRhs)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION){
                thenValue = thenRhs.getText();
            } else{
                thenValue = '(' + thenRhs.getText() + ')';
            }
            final String elseValue;
            final PsiExpression elseRhs = elseAssign.getRExpression();
            assert elseRhs != null;
            if(ParenthesesUtils.getPrecendence(elseRhs)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION){
                elseValue = elseRhs.getText();
            } else{
                elseValue = '(' + elseRhs.getText() + ')';
            }
            final String conditionText;
            if(ParenthesesUtils.getPrecendence(condition)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION){
                conditionText = condition.getText();
            } else{
                conditionText = '(' + condition.getText() + ')';
            }

            replaceStatement(lhsText + operator + conditionText + '?' +
                    thenValue + ':' + elseValue + ';',
                             ifStatement);
        } else
        if(ReplaceIfWithConditionalPredicate.isReplaceableReturn(ifStatement)){
            final PsiExpression condition = ifStatement.getCondition();
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            final PsiReturnStatement thenReturn =
                    (PsiReturnStatement) ConditionalUtils.stripBraces(thenBranch);
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            final PsiReturnStatement elseReturn =
                    (PsiReturnStatement) ConditionalUtils.stripBraces(elseBranch);

            final String thenValue;
            final PsiExpression thenReturnValue = thenReturn.getReturnValue();
            if(ParenthesesUtils.getPrecendence(thenReturnValue)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION){
                thenValue = thenReturnValue.getText();
            } else{
                thenValue = '(' + thenReturnValue.getText() + ')';
            }
            final String elseValue;
            final PsiExpression elseReturnValue = elseReturn.getReturnValue();
            if(ParenthesesUtils.getPrecendence(elseReturnValue)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION){
                elseValue = elseReturnValue.getText();
            } else{
                elseValue = '(' + elseReturnValue.getText() + ')';
            }
            final String conditionText;
            if(ParenthesesUtils.getPrecendence(condition)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION){
                conditionText = condition.getText();
            } else{
                conditionText = '(' + condition.getText() + ')';
            }

            replaceStatement("return " + conditionText + '?' + thenValue + ':' +
                    elseValue + ';',
                             ifStatement);
        } else
        if(ReplaceIfWithConditionalPredicate.isReplaceableImplicitReturn(ifStatement)){
            final PsiExpression condition = ifStatement.getCondition();
            final PsiStatement rawThenBranch = ifStatement.getThenBranch();
            final PsiReturnStatement thenBranch =
                    (PsiReturnStatement) ConditionalUtils.stripBraces(rawThenBranch);
            final PsiReturnStatement elseBranch =
                    PsiTreeUtil.getNextSiblingOfType(ifStatement, PsiReturnStatement.class);

            final String thenValue;
            final PsiExpression thenReturnValue = thenBranch.getReturnValue();
            if(ParenthesesUtils.getPrecendence(thenReturnValue)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION){
                thenValue = thenReturnValue.getText();
            } else{
                thenValue = '(' + thenReturnValue.getText() + ')';
            }
            final String elseValue;
            assert elseBranch != null;
            final PsiExpression elseReturnValue = elseBranch.getReturnValue();
            if(ParenthesesUtils.getPrecendence(elseReturnValue)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION){
                elseValue = elseReturnValue.getText();
            } else{
                elseValue = '(' + elseReturnValue.getText() + ')';
            }
            final String conditionText;
            if(ParenthesesUtils.getPrecendence(condition)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION){
                conditionText = condition.getText();
            } else{
                conditionText = '(' + condition.getText() + ')';
            }

            replaceStatement("return " + conditionText + '?' + thenValue + ':' +
                    elseValue + ';',
                             ifStatement);
            elseBranch.delete();
        }
    }
}
