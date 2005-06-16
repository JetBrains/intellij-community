package com.siyeh.ig.initialization;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class AbstractMethodCallInConstructorInspection extends MethodInspection{
    public String getDisplayName(){
        return "Abstract method call in constructor";
    }

    public String getGroupDisplayName(){
        return GroupNames.INITIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Call to abstract method #ref during object construction #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new AbstractMethodCallInConstructorVisitor();
    }

    private static class AbstractMethodCallInConstructorVisitor
                                                                extends BaseInspectionVisitor{

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call){
            super.visitMethodCallExpression(call);
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(call, PsiMethod.class);
            if(method == null){
                return;
            }
            if(!method.isConstructor()){
                return;
            }
            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final PsiMethod calledMethod = (PsiMethod) methodExpression.resolve();
            if(calledMethod == null){
                return;
            }
            if(calledMethod.isConstructor()){
                return;
            }
            if(!calledMethod.hasModifierProperty(PsiModifier.ABSTRACT)){
                return;
            }
            final PsiClass calledMethodClass = calledMethod.getContainingClass();
            if(calledMethodClass == null)
            {
                return;
            }
            final PsiClass methodClass = method.getContainingClass();
            if(!calledMethodClass.equals(methodClass)){
                return;
            }
            registerMethodCallError(call);
        }
    }
}
