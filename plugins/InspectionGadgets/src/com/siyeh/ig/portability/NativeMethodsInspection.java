package com.siyeh.ig.portability;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class NativeMethodsInspection extends MethodInspection{
    public String getID(){
        return "NativeMethod";
    }

    public String getDisplayName(){
        return "Native method";
    }

    public String getGroupDisplayName(){
        return GroupNames.PORTABILITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Methods declared '#ref' are non-portable #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new NativeMethodVisitor();
    }

    private static class NativeMethodVisitor extends BaseInspectionVisitor{


        public void visitMethod(@NotNull PsiMethod method){
            if(!method.hasModifierProperty(PsiModifier.NATIVE)){
                return;
            }
            registerModifierError(PsiModifier.NATIVE, method);
        }
    }
}
