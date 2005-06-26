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
        final PsiExpression expression = (PsiExpression) element;

        if(element instanceof PsiLiteralExpression ||
                element instanceof PsiClassObjectAccessExpression){
            return false;
        }
        if(!PsiUtil.isConstantExpression(expression)){
            return false;
        }
        final PsiManager manager= element.getManager();
        final PsiConstantEvaluationHelper helper =
                manager.getConstantEvaluationHelper();
        final Object value = helper.computeConstantExpression(expression);
        if(value == null)
        {
            return false;
        }
        final PsiElement parent = element.getParent();
        if(!(parent instanceof PsiExpression)){
            return true;
        }
        return !PsiUtil.isConstantExpression((PsiExpression) parent);
    }
}
