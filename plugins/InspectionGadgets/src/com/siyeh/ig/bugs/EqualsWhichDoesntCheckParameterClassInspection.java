package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.TypeUtils;

public class EqualsWhichDoesntCheckParameterClassInspection
        extends MethodInspection{
    public String getDisplayName(){
        return "'equals()' method which doesn't check class of parameter";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "#ref should check the class of it's parameter #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new EqualsWhichDoesntCheckParameterClassVisitor(this,
                                                               inspectionManager,
                                                               onTheFly);
    }

    private static class EqualsWhichDoesntCheckParameterClassVisitor
            extends BaseInspectionVisitor{
        private static final String EQUALS_METHOD_NAME = "equals";

        private EqualsWhichDoesntCheckParameterClassVisitor(BaseInspection inspection,
                                                            InspectionManager inspectionManager,
                                                            boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method){
            // note: no call to super
            final String name = method.getName();
            if(!EQUALS_METHOD_NAME.equals(name)){
                return;
            }
            if(!method.hasModifierProperty(PsiModifier.PUBLIC)){
                return;
            }
            final PsiParameterList paramList = method.getParameterList();
            if(paramList == null){
                return;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            if(parameters.length != 1){
                return;
            }
            final PsiParameter parameter = parameters[0];
            final PsiType argType = parameter.getType();
            if(!TypeUtils.isJavaLangObject(argType)){
                return;
            }
            final PsiCodeBlock body = method.getBody();
            if(body == null){
                return;
            }
            if(isParameterChecked(body, parameter)){
                return;
            }
            registerMethodError(method);
        }

        private boolean isParameterChecked(PsiCodeBlock body,
                                           PsiParameter parameter){
            final ParameterClassCheckVisitor visitor =
                    new ParameterClassCheckVisitor(parameter);
            body.accept(visitor);
            return visitor.isChecked();
        }
    }

}
