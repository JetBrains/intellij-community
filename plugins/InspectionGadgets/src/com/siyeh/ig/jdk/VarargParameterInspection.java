package com.siyeh.ig.jdk;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.siyeh.ig.*;

public class VarargParameterInspection extends MethodInspection {
    public String getID(){
        return "VariableArgumentMethod";
    }
    public String getDisplayName() {
        return "Variable argument method";
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Variable argument method '#ref' #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new VarargParameterVisitor(this, inspectionManager, onTheFly);
    }

    private static class VarargParameterVisitor extends BaseInspectionVisitor {
        private VarargParameterVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList == null) {
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters == null) {
                return;
            }
            for(final PsiParameter parameter : parameters){
                if(parameter.isVarArgs()){
                    registerMethodError(method);
                    return;
                }
            }
        }

    }

}