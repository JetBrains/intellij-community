package com.siyeh.ig.portability;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;

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

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new NativeMethodVisitor(this, inspectionManager, onTheFly);
    }

    private static class NativeMethodVisitor extends BaseInspectionVisitor{
        private NativeMethodVisitor(BaseInspection inspection,
                                    InspectionManager inspectionManager,
                                    boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method){
            if(!method.hasModifierProperty(PsiModifier.NATIVE)){
                return;
            }
            registerModifierError(PsiModifier.NATIVE, method);
        }
    }
}
