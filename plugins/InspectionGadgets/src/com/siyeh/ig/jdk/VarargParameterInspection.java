package com.siyeh.ig.jdk;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;

public class VarargParameterInspection extends StatementInspection {
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
            super.visitMethod(method);
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList == null) {
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters == null) {
                return;
            }
            for (int i = 0; i < parameters.length; i++) {
                final PsiParameter parameter = parameters[i];
                if (parameter.isVarArgs()) {
                    registerMethodError(method);
                    return;
                }
            }
        }

    }

}