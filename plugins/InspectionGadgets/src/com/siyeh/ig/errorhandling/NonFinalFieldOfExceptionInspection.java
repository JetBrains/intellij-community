package com.siyeh.ig.errorhandling;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;

public class NonFinalFieldOfExceptionInspection extends FieldInspection{
    public String getDisplayName(){
        return "Non-final field of exception class";
    }

    public String getGroupDisplayName(){
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Non-final field '#ref' of exception class #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new NonFinalFieldOfExceptionVisitor();
    }

    private static class NonFinalFieldOfExceptionVisitor
            extends BaseInspectionVisitor{
        public void visitField(PsiField field){
            super.visitField(field);
            if(field.hasModifierProperty(PsiModifier.FINAL)){
                return;
            }
            final PsiClass containingClass = field.getContainingClass();
            if(containingClass == null){
                return;
            }
            if(!ClassUtils.isSubclass(containingClass, "java.lang.Exception")){
                return;
            }
            registerFieldError(field);
        }
    }
}
