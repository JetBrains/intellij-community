package com.siyeh.ig.threading;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class NonSynchronizedMethodOverridesSynchronizedMethodInspection
                                                                        extends MethodInspection{
    public String getDisplayName(){
        return "Non-synchronized method overrides synchronized method";
    }

    public String getGroupDisplayName(){
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Non-synchronized method '#ref' overrides synchronized method #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new NonSynchronizedMethodOverridesSynchronizedMethodVisitor();
    }

    private static class NonSynchronizedMethodOverridesSynchronizedMethodVisitor
            extends BaseInspectionVisitor{
        public void visitMethod(@NotNull PsiMethod method){
            //no call to super, so we don't drill into anonymous classes
            if(method.isConstructor()){
                return;
            }
            if(method.hasModifierProperty(PsiModifier.SYNCHRONIZED)){
                return;
            }
            final PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(method);
            for(final PsiMethod superMethod : superMethods){
                if(superMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)){
                    registerMethodError(method);
                    return;
                }
            }
        }
    }
}
