package com.siyeh.ipp.parenthesis;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import com.siyeh.ipp.psiutils.ErrorUtil;

class UnnecessaryParenthesesPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiParenthesizedExpression)){
            return false;
        }
        if(ErrorUtil.containsError(element)){
            return false;
        }
        final PsiParenthesizedExpression expression =
                (PsiParenthesizedExpression) element;
        final PsiElement parent = expression.getParent();
        if(!(parent instanceof PsiExpression)){
            return true;
        }
        final PsiExpression body = expression.getExpression();
        if(body instanceof PsiParenthesizedExpression){
            return true;
        }

        final int parentPrecendence = ParenthesesUtils.getPrecendence(
                        (PsiExpression) parent);
        final int childPrecendence = ParenthesesUtils.getPrecendence(body);
        if(parentPrecendence > childPrecendence){
            return true;
        } else if(parentPrecendence == childPrecendence){

            if(parent instanceof PsiBinaryExpression &&
                                    body instanceof PsiBinaryExpression){
                final PsiJavaToken parentSign =
                        ((PsiBinaryExpression) parent).getOperationSign();
                final IElementType parentOperator = parentSign.getTokenType();
                final PsiJavaToken childSign =
                        ((PsiBinaryExpression) body).getOperationSign();
                final IElementType childOperator = childSign.getTokenType();

                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) parent;
                final PsiExpression lhs = binaryExpression.getLOperand();
                return lhs.equals(expression) &&
                               parentOperator.equals(childOperator);
            } else{
                return false;
            }
        } else{
            return false;
        }
    }
}