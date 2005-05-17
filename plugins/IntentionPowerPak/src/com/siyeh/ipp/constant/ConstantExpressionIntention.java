package com.siyeh.ipp.constant;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;

public class ConstantExpressionIntention extends Intention{
    protected PsiElementPredicate getElementPredicate(){
        return new ConstantExpressionPredicate();
    }

    public String getText(){
        return "Compute constant value";
    }

    public String getFamilyName(){
        return "Compute Constant Value";
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiExpression expression =
                (PsiExpression) element;
        final PsiManager psiManager = expression.getManager();
        final PsiConstantEvaluationHelper helper =
                psiManager.getConstantEvaluationHelper();
        final Object value = helper.computeConstantExpression(expression);
        final String newExpression;
        if(value instanceof String){
            newExpression = '\"' + StringUtil
                    .escapeStringCharacters((String) value) +
                    '\"';
        } else if(value == null){
            newExpression = "null";
        } else{
            newExpression = String.valueOf(value);
        }
        replaceExpression(newExpression, expression);
    }
}
