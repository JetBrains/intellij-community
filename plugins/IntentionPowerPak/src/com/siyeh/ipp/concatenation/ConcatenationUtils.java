package com.siyeh.ipp.concatenation;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;

class ConcatenationUtils{
    private ConcatenationUtils(){
        super();
    }

    public static boolean isConcatenation(PsiElement element){
        if(!(element instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression expression = (PsiBinaryExpression) element;
        final PsiJavaToken sign = expression.getOperationSign();
        if(sign == null){
            return false;
        }
        final IElementType tokenType = sign.getTokenType();
        if(!tokenType.equals(JavaTokenType.PLUS)){
            return false;
        }
        final PsiExpression lhs = expression.getLOperand();
        if(lhs == null){
            return false;
        }
        final PsiType lhsType = lhs.getType();
        if(lhsType == null){
            return false;
        }
        final PsiExpression rhs = expression.getROperand();
        if(rhs == null){
            return false;
        }
        final String typeName = lhsType.getCanonicalText();
        return "java.lang.String".equals(typeName);
    }
}
