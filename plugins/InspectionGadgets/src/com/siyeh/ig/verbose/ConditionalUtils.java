package com.siyeh.ig.verbose;

import com.intellij.psi.*;

public class ConditionalUtils{
    private ConditionalUtils(){
        super();
    }

    public static PsiStatement stripBraces(PsiStatement branch){
        if(branch instanceof PsiBlockStatement){
            final PsiBlockStatement block = (PsiBlockStatement) branch;
            final PsiCodeBlock codeBlock = block.getCodeBlock();
            final PsiStatement[] statements = codeBlock.getStatements();
            if(statements.length == 1){
                return statements[0];
            } else{
                return block;
            }
        } else{
            return branch;
        }
    }

    public static boolean isReturn(PsiStatement statement, String value){
        if(statement == null){
            return false;
        }
        if(!(statement instanceof PsiReturnStatement)){
            return false;
        }
        final PsiReturnStatement returnStatement =
                (PsiReturnStatement) statement;
        if(returnStatement.getReturnValue() == null){
            return false;
        }
        final PsiExpression returnValue = returnStatement.getReturnValue();
        final String returnValueText = returnValue.getText();
        return value.equals(returnValueText);
    }

    public static boolean isAssignment(PsiStatement statement, String value){
        if(statement == null){
            return false;
        }
        if(!(statement instanceof PsiExpressionStatement)){
            return false;
        }
        final PsiExpressionStatement expressionStatement =
                (PsiExpressionStatement) statement;
        final PsiExpression expression = expressionStatement.getExpression();
        if(!(expression instanceof PsiAssignmentExpression)){
            return false;
        }
        final PsiAssignmentExpression assignment =
                (PsiAssignmentExpression) expression;
        final PsiExpression rhs = assignment.getRExpression();
        final PsiExpression lhs = assignment.getLExpression();
        if(lhs == null || rhs == null){
            return false;
        }
        final String rhsText = rhs.getText();
        return value.equals(rhsText);
    }

    public static boolean isAssignment(PsiStatement statement){
        if(!(statement instanceof PsiExpressionStatement)){
            return false;
        }
        final PsiExpressionStatement expressionStatement =
                (PsiExpressionStatement) statement;
        final PsiExpression expression = expressionStatement.getExpression();
        return expression instanceof PsiAssignmentExpression;
    }
}
