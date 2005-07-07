package com.siyeh.ipp.concatenation;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.Nullable;

class StringConcatPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(element instanceof PsiJavaToken){
            final PsiJavaToken token = (PsiJavaToken) element;
            final IElementType tokenType = token.getTokenType();
            if(!tokenType.equals(JavaTokenType.PLUS)){
                return false;
            }
        } else if(!(element instanceof PsiWhiteSpace)){
            return false;
        }
        final PsiElement parent = element.getParent();
        if(!(parent instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) parent;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(!tokenType.equals(JavaTokenType.PLUS)){
            return false;
        }
        final PsiType type = binaryExpression.getType();
        if(type == null){
            return false;
        }
        if(!type.equalsToText("java.lang.String")){
            return false;
        }
        final PsiExpression subexpression = getSubexpression(binaryExpression);
        return PsiUtil.isConstantExpression(subexpression);
    }

    /**
     * Returns the smallest subexpression (if precendence allows it). example:
     * variable + 2 + 3 normally gets evaluated left to right -> (variable + 2) +
     * 3 this method returns the right most legal subexpression -> 2 + 3
     */
    @Nullable private static PsiBinaryExpression getSubexpression(PsiBinaryExpression expression){
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression.copy();
        final PsiExpression rhs = binaryExpression.getROperand();
        if(rhs == null){
            return null;
        }
        final PsiExpression lhs = binaryExpression.getLOperand();
        if(!(lhs instanceof PsiBinaryExpression)){
            return expression;
        }
        final PsiBinaryExpression lhsBinaryExpression = (PsiBinaryExpression) lhs;
        final PsiExpression leftSide = lhsBinaryExpression.getROperand();
        if(leftSide == null)
        {
            return null;
        }
        try{
            lhs.replace(leftSide);
        } catch(Throwable e){
            return null;
        }
        return binaryExpression;
    }
}