package com.siyeh.ipp.concatenation;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;

class AppendChainPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiMethodCallExpression)){
            return false;
        }
        final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        if(!AppendUtil.isAppend(call))
        {
            return false;
        }
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if(!(qualifier instanceof PsiMethodCallExpression)){
            return false;
        }
        final PsiMethodCallExpression qualifierCall = (PsiMethodCallExpression) element;
        if(!AppendUtil.isAppend(qualifierCall)){
            return false;
    }
        final PsiElement parent = element.getParent();
        if(parent instanceof PsiExpressionStatement)
            return true;
        if(parent instanceof PsiLocalVariable &&
                                parent.getParent() instanceof PsiDeclarationStatement &&
                       ( (PsiDeclarationStatement)(parent.getParent())).getDeclaredElements().length == 1)
            return true;
        if(parent instanceof PsiAssignmentExpression &&
                                parent.getParent() instanceof PsiExpressionStatement)
            return true;
        return false;
    }

}
