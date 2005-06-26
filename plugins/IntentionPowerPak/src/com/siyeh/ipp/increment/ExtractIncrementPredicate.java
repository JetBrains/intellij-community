package com.siyeh.ipp.increment;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class ExtractIncrementPredicate
        implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiPrefixExpression) &&
                !(element instanceof PsiPostfixExpression)){
            return false;
        }
        if(ErrorUtil.containsError(element)){
            return false;
        }
        final PsiJavaToken sign;
        if(element instanceof PsiPostfixExpression){
            sign = ((PsiPostfixExpression) element).getOperationSign();
        } else{
            sign = ((PsiPrefixExpression) element).getOperationSign();
        }

        final IElementType tokenType = sign.getTokenType();
        if(!JavaTokenType.PLUSPLUS.equals(tokenType) &&
                !JavaTokenType.MINUSMINUS.equals(tokenType)){
            return false;
        }
        final PsiElement parent = element.getParent();
        if(parent instanceof PsiExpressionStatement){
            return false;
        }
        final PsiStatement containingStatement = PsiTreeUtil
                .getParentOfType(element, PsiStatement.class);
        return containingStatement != null;
    }
}
