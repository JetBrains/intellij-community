package com.siyeh.ipp.bool;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.BoolUtils;
import com.siyeh.ipp.psiutils.ComparisonUtils;
import com.siyeh.ipp.psiutils.ParenthesesUtils;

public class DemorgansIntention extends MutablyNamedIntention{
    protected String getTextForElement(PsiElement element){
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) element;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(tokenType.equals(JavaTokenType.ANDAND)){
            return "Replace && with ||";
        } else{
            return "Replace || with &&";
        }
    }

    public String getFamilyName(){
        return "DeMorgan Law";
    }

    public PsiElementPredicate getElementPredicate(){
        return new ConjunctionPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        if(isFileReadOnly(project, file)){
            return;
        }
        PsiBinaryExpression exp =
                (PsiBinaryExpression) findMatchingElement(file, editor);
        final PsiJavaToken sign = exp.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        PsiElement parent = exp.getParent();
        while(isConjunctionExpression(parent, tokenType)){
            exp = (PsiBinaryExpression) parent;
            parent = exp.getParent();
        }
        final String newExpression =
                convertConjunctionExpression(exp, tokenType);
        replaceExpressionWithNegatedExpressionString(project, newExpression,
                                                     exp);
    }

    private String convertConjunctionExpression(PsiBinaryExpression exp,
                                                IElementType tokenType){
        final String lhsText;
        final PsiExpression lhs = exp.getLOperand();
        if(isConjunctionExpression(lhs, tokenType)){
            lhsText = convertConjunctionExpression((PsiBinaryExpression) lhs,
                                                   tokenType);
        } else{
            lhsText = convertLeafExpression(lhs);
        }
        final String rhsText;
        final PsiExpression rhs = exp.getROperand();
        if(isConjunctionExpression(rhs, tokenType)){
            rhsText = convertConjunctionExpression((PsiBinaryExpression) rhs,
                                                   tokenType);
        } else{
            rhsText = convertLeafExpression(rhs);
        }

        final String flippedConjunction;
        if(tokenType.equals(JavaTokenType.ANDAND)){
            flippedConjunction = "||";
        } else{
            flippedConjunction = "&&";
        }

        return lhsText + flippedConjunction + rhsText;
    }

    private static String convertLeafExpression(PsiExpression condition){
        if(BoolUtils.isNegation(condition)){
            final PsiExpression negated = BoolUtils.getNegated(condition);
            if(ParenthesesUtils.getPrecendence(negated) >
                       ParenthesesUtils.OR_PRECEDENCE){
                return '(' + negated.getText() + ')';
            }
            return negated.getText();
        } else if(ComparisonUtils.isComparison(condition)){
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) condition;
            final PsiJavaToken sign = binaryExpression.getOperationSign();
            final String operator = sign.getText();
            final String negatedComparison =
                    ComparisonUtils.getNegatedComparison(operator);
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            return lhs.getText() + negatedComparison + rhs.getText();
        } else if(ParenthesesUtils.getPrecendence(condition) >
                          ParenthesesUtils.PREFIX_PRECEDENCE){
            return "!(" + condition.getText() + ')';
        } else{
            return '!' + condition.getText();
        }
    }

    private static boolean isConjunctionExpression(PsiElement exp,
                                                   IElementType conjunctionType){
        if(!(exp instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression binExp = (PsiBinaryExpression) exp;
        final PsiJavaToken sign = binExp.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        return tokenType.equals(conjunctionType);
    }
}
