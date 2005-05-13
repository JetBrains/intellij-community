package com.siyeh.ig.security;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class NonFinalCloneInspection extends MethodInspection{
    public String getDisplayName(){
        return "Non-final 'clone()' in secure context";
    }

    public String getGroupDisplayName(){
        return GroupNames.SECURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Non-final '#ref()' method, compromising security #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new NonFinalCloneVisitor(this, inspectionManager, onTheFly);
    }

    private static class NonFinalCloneVisitor extends BaseInspectionVisitor{
        private NonFinalCloneVisitor(BaseInspection inspection,
                                     InspectionManager inspectionManager,
                                     boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(@NotNull PsiMethod method){
            super.visitMethod(method);
            final String name = method.getName();
            if(!"clone".equals(name)){
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if(parameterList == null){
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if(parameters == null || parameters.length != 0){
                return;
            }
            if(method.hasModifierProperty(PsiModifier.FINAL)
                    || method.hasModifierProperty(PsiModifier.ABSTRACT)){
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null){
                return;
            }

            if(containingClass.hasModifierProperty(PsiModifier.FINAL)
                    || containingClass.isInterface()){
                return;
            }
            registerMethodError(method);
        }
    }
}
