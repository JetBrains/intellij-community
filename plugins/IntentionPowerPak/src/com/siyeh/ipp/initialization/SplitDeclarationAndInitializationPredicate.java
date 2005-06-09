package com.siyeh.ipp.initialization;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiClass;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

public class SplitDeclarationAndInitializationPredicate
        implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiField)){
            return false;
        }
        final PsiField field = (PsiField) element;
        if(ErrorUtil.containsError(field)){
            return false;
        }
        final PsiClass aClass = field.getContainingClass();
        if (aClass == null || aClass.isInterface()){
            return false;
        }
        final PsiExpression initializer = field.getInitializer();
        return initializer != null;
    }
}
