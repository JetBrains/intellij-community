package com.siyeh.ipp.concatenation;

import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClass;

public class AppendUtil{
    public static boolean isAppend(PsiMethodCallExpression call)
    {
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        if(methodExpression == null){
            return false;
        }
        final String callName = methodExpression.getReferenceName();
        if(!"append".equals(callName)){
            return false;
        }
        final PsiMethod method = call.resolveMethod();
        if(method == null){
            return false;
        }
        final PsiClass containingClass = method.getContainingClass();
        if(containingClass == null){
            return false;
        }
        final String name = containingClass.getQualifiedName();
        return "java.lang.StringBuffer".equals(name)||
                       "java.lang.StringBuilder".equals(name);
    }
}
