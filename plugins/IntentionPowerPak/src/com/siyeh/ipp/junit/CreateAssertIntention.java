package com.siyeh.ipp.junit;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.BoolUtils;

public class CreateAssertIntention extends Intention{
    public String getText(){
        return "Create JUnit assertion";
    }

    public String getFamilyName(){
        return "Create JUnit assertion";
    }

    public PsiElementPredicate getElementPredicate(){
        return new CreateAssertPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiExpressionStatement statement =
                (PsiExpressionStatement) element;
        assert statement != null;
        final PsiExpression expression = statement.getExpression();
        if(BoolUtils.isNegation(expression)){
            final String newExpression = "assertFalse(" +
                    BoolUtils.getNegatedExpressionText(expression) + ");";
            replaceStatement(newExpression,
                             statement);
        } else if(isNullComparison(expression)){
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) expression;
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            final PsiExpression comparedExpression;
            if(isNull(lhs)){
                comparedExpression = rhs;
            } else{
                comparedExpression = lhs;
            }
            final String newExpression = "assertNull(" +
                    comparedExpression.getText() + ");";
            replaceStatement(newExpression,
                             statement);
        } else if(isEqualityComparison(expression)){
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) expression;
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            final PsiExpression comparedExpression;
            final PsiExpression comparingExpression;
            if(rhs instanceof PsiLiteralExpression){
                comparedExpression = rhs;
                comparingExpression = lhs;
            } else{
                comparedExpression = lhs;
                comparingExpression = rhs;
            }
            final PsiType type = lhs.getType();
            final String newExpression;
            if(PsiType.DOUBLE.equals(type) || PsiType.FLOAT.equals(type)){
                newExpression = "assertEquals(" +
                        comparedExpression.getText() + ", " +
                        comparingExpression.getText() + ", 0.0);";
            } else if(type instanceof PsiPrimitiveType){
                newExpression = "assertEquals(" +
                        comparedExpression.getText() + ", " +
                        comparingExpression.getText() + ");";
            } else{
                newExpression = "assertSame(" +
                        comparedExpression.getText() + ", " +
                        comparingExpression.getText() + ");";
            }
            replaceStatement(newExpression,
                             statement);
        } else if(isEqualsExpression(expression)){
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression) expression;
            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            final PsiExpression comparedExpression =
                    methodExpression.getQualifierExpression();
            final PsiExpressionList argList = call.getArgumentList();
            assert argList != null;
            final PsiExpression comparingExpression = argList.getExpressions()[0];
            final String newExpression;
            if(comparingExpression instanceof PsiLiteralExpression){
                newExpression = "assertEquals(" +
                        comparingExpression.getText() + ", " +
                        comparedExpression.getText() + ");";
            } else{
                newExpression = "assertEquals(" +
                        comparedExpression.getText() + ", " +
                        comparingExpression.getText() + ");";
            }
            replaceStatement(newExpression,
                             statement);
        } else{
            final String newExpression =
                    "assertTrue(" + expression.getText() + ");";
            replaceStatement(newExpression,
                             statement);
        }
    }

    private static boolean isEqualsExpression(PsiExpression expression){
        if(!(expression instanceof PsiMethodCallExpression)){
            return false;
        }
        final PsiMethodCallExpression call =
                (PsiMethodCallExpression) expression;
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        if(methodExpression == null){
            return false;
        }
        final String methodName = methodExpression.getReferenceName();
        if(!"equals".equals(methodName)){
            return false;
        }
        final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
        if(qualifier == null){
            return false;
        }
        final PsiExpressionList argList = call.getArgumentList();
        if(argList == null){
            return false;
        }
        final PsiExpression[] expressions = argList.getExpressions();
        return expressions != null && expressions.length == 1 &&
                expressions[0] != null;
    }

    private static boolean isEqualityComparison(PsiExpression expression){
        if(!(expression instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        if(sign == null){
            return false;
        }
        final IElementType tokenType = sign.getTokenType();
        return JavaTokenType.EQEQ.equals(tokenType);
    }

    private static boolean isNullComparison(PsiExpression expression){
        if(!(expression instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        if(sign == null){
            return false;
        }
        final IElementType tokenType = sign.getTokenType();
        if(!JavaTokenType.EQEQ.equals(tokenType)){
            return false;
        }
        final PsiExpression lhs = binaryExpression.getLOperand();
        if(isNull(lhs)){
            return true;
        }
        final PsiExpression Rhs = binaryExpression.getROperand();
        return isNull(Rhs);
    }

    private static boolean isNull(PsiExpression expression){
        if(!(expression instanceof PsiLiteralExpression)){
            return false;
        }
        final String text = expression.getText();
        return "null".equals(text);
    }
}
