package com.siyeh.ipp.constant;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class ConstantExpressionPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiExpression)){
            return false;
        }
        if(ErrorUtil.containsError(element)){
            return false;
        }

        if(element instanceof PsiLiteralExpression ||
                                element instanceof PsiClassObjectAccessExpression){
            return false;
        }
        if(!PsiUtil.isConstantExpression((PsiExpression) element)){
            return false;
        }
        final PsiElement parent = element.getParent();
        if(!(parent instanceof PsiExpression)){
            return true;
        }
        return !PsiUtil.isConstantExpression((PsiExpression) parent);
    }
}
