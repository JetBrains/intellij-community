package com.siyeh.ipp.trivialif;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.*;
import com.siyeh.ipp.psiutils.ConditionalUtils;
import com.siyeh.ipp.psiutils.ParenthesesUtils;

public class ReplaceIfWithConditionalIntention extends Intention {
    public ReplaceIfWithConditionalIntention(Project project) {
        super(project);
    }

    public String getText() {
        return "Replace if-else with ?:";
    }

    public String getFamilyName() {
        return "Replace If Else With Conditional";
    }

    public PsiElementPredicate getElementPredicate() {
        return new ReplaceIfWithConditionalPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        final PsiJavaToken token = (PsiJavaToken) findMatchingElement(file, editor);
        final PsiIfStatement ifStatement = (PsiIfStatement) token.getParent();
        if (ReplaceIfWithConditionalPredicate.isReplaceableAssignment(ifStatement)) {
            final PsiExpression condition = ifStatement.getCondition();
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            final PsiExpressionStatement strippedThenBranch = (PsiExpressionStatement) ConditionalUtils.stripBraces(thenBranch);
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            final PsiExpressionStatement strippedElseBranch = (PsiExpressionStatement) ConditionalUtils.stripBraces(elseBranch);
            final PsiAssignmentExpression thenAssign = (PsiAssignmentExpression) strippedThenBranch.getExpression();
            final PsiAssignmentExpression elseAssign = (PsiAssignmentExpression) strippedElseBranch.getExpression();
            final PsiExpression lhs = thenAssign.getLExpression();
            final String lhsText = lhs.getText();
            final PsiJavaToken sign = thenAssign.getOperationSign();
            final String operator = sign.getText();
            final String thenValue;
            final PsiExpression thenRhs = thenAssign.getRExpression();
            if (ParenthesesUtils.getPrecendence(thenRhs)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION) {
                thenValue = thenRhs.getText();
            } else {
                thenValue = '(' + thenRhs.getText() + ')';

            }
            final String elseValue;
            final PsiExpression elseRhs = elseAssign.getRExpression();
            if (ParenthesesUtils.getPrecendence(elseRhs)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION) {
                elseValue = elseRhs.getText();
            } else {
                elseValue = '(' + elseRhs.getText() + ')';

            }
            final String conditionText;
            if (ParenthesesUtils.getPrecendence(condition)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION) {
                conditionText = condition.getText();
            } else {
                conditionText = '(' + condition.getText() + ')';

            }

            replaceStatement(project,
                    lhsText + operator + conditionText + '?' + thenValue + ':' + elseValue + ';',
                    ifStatement);
        } else if (ReplaceIfWithConditionalPredicate.isReplaceableReturn(ifStatement)) {
            final PsiExpression condition = ifStatement.getCondition();
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            final PsiReturnStatement thenReturn = (PsiReturnStatement) ConditionalUtils.stripBraces(thenBranch);
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            final PsiReturnStatement elseReturn = (PsiReturnStatement) ConditionalUtils.stripBraces(elseBranch);

            final String thenValue;
            final PsiExpression thenReturnValue = thenReturn.getReturnValue();
            if (ParenthesesUtils.getPrecendence(thenReturnValue)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION) {
                thenValue = thenReturnValue.getText();
            } else {
                thenValue = '(' + thenReturnValue.getText() + ')';

            }
            final String elseValue;
            final PsiExpression elseReturnValue = elseReturn.getReturnValue();
            if (ParenthesesUtils.getPrecendence(elseReturnValue)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION) {
                elseValue = elseReturnValue.getText();
            } else {
                elseValue = '(' + elseReturnValue.getText() + ')';

            }
            final String conditionText;
            if (ParenthesesUtils.getPrecendence(condition)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION) {
                conditionText = condition.getText();
            } else {
                conditionText = '(' + condition.getText() + ')';

            }

            replaceStatement(project,
                    "return " + conditionText + '?' + thenValue + ':' + elseValue + ';',
                    ifStatement);
        } else if (ReplaceIfWithConditionalPredicate.isReplaceableImplicitReturn(ifStatement)) {
            final PsiExpression condition = ifStatement.getCondition();
            final PsiStatement rawThenBranch = ifStatement.getThenBranch();
            final PsiReturnStatement thenBranch = (PsiReturnStatement) ConditionalUtils.stripBraces(rawThenBranch);
            PsiElement nextStatement = ifStatement.getNextSibling();
            while (nextStatement instanceof PsiWhiteSpace) {
                nextStatement = nextStatement.getNextSibling();
            }

            final PsiReturnStatement elseBranch = (PsiReturnStatement) nextStatement;

            final String thenValue;
            final PsiExpression thenReturnValue = thenBranch.getReturnValue();
            if (ParenthesesUtils.getPrecendence(thenReturnValue)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION) {
                thenValue = thenReturnValue.getText();
            } else {
                thenValue = '(' + thenReturnValue.getText() + ')';

            }
            final String elseValue;
            final PsiExpression elseReturnValue = elseBranch.getReturnValue();
            if (ParenthesesUtils.getPrecendence(elseReturnValue)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION) {
                elseValue = elseReturnValue.getText();
            } else {
                elseValue = '(' + elseReturnValue.getText() + ')';

            }
            final String conditionText;
            if (ParenthesesUtils.getPrecendence(condition)
                    <= ParenthesesUtils.CONDITIONAL_EXPRESSION_EXPRESSION) {
                conditionText = condition.getText();
            } else {
                conditionText = '(' + condition.getText() + ')';

            }

            replaceStatement(project,
                    "return " + conditionText + '?' + thenValue + ':' + elseValue + ';',
                    ifStatement);
            elseBranch.delete();
        }
    }

}
