package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.*;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.ig.fixes.RenameFix;

import javax.swing.*;

public class ParameterNameDiffersFromOverriddenParameterInspection
        extends MethodInspection{
    public boolean m_ignoreSingleCharacterNames = true;

    public String getDisplayName(){
        return "Parameter name differs from parameter in overridden method";
    }

    public String getGroupDisplayName(){
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel("Ignore if overriden parameter contains only one character",
                                              this,
                                              "m_ignoreSingleCharacterNames");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        final PsiParameter parameter = (PsiParameter) location.getParent();
        final String parameterName = parameter.getName();
        final PsiMethod method =
                (PsiMethod) PsiTreeUtil.getParentOfType(parameter,
                                                        PsiMethod.class);
        final PsiMethod[] superMethods =
                PsiSuperMethodUtil.findSuperMethods(method);
        final PsiParameterList methodParamList = method.getParameterList();
        final int index = methodParamList.getParameterIndex(parameter);
        for(int i = 0; i < superMethods.length; i++){
            final PsiMethod superMethod = superMethods[i];
            final PsiParameterList parameterList =
                    superMethod.getParameterList();
            if(parameterList != null){
                final PsiParameter[] parameters = parameterList.getParameters();
                if(parameters != null){
                    final String superParameterName =
                            parameters[index].getName();
                    if(superParameterName != null &&
                               !superParameterName.equals(parameterName)){
                        return new RenameFix(superParameterName);
                    }
                }
            }
        }
        return null;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        final PsiParameter parameter = (PsiParameter) location.getParent();
        final String parameterName = parameter.getName();
        final PsiMethod method =
                (PsiMethod) PsiTreeUtil.getParentOfType(parameter,
                                                        PsiMethod.class);
        final PsiMethod[] superMethods =
                PsiSuperMethodUtil.findSuperMethods(method);
        final PsiParameterList methodParamList = method.getParameterList();
        final int index = methodParamList.getParameterIndex(parameter);
        for(int i = 0; i < superMethods.length; i++){
            final PsiMethod superMethod = superMethods[i];
            final PsiParameterList parameterList =
                    superMethod.getParameterList();
            if(parameterList != null){
                final PsiParameter[] parameters = parameterList.getParameters();
                if(parameters != null){
                    final String superParameterName =
                            parameters[index].getName();
                    if(superParameterName != null &&
                               !superParameterName.equals(parameterName)){
                        return "Parameter name '#ref' is different from parameter '" +
                                       superParameterName + "'overridden #loc";
                    }
                }
            }
        }
        return "";// this can't happen
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new ParameterNameDiffersFromOverriddenParameterVisitor(this,
                                                                      inspectionManager,
                                                                      onTheFly);
    }

    private class ParameterNameDiffersFromOverriddenParameterVisitor
            extends BaseInspectionVisitor{
        private ParameterNameDiffersFromOverriddenParameterVisitor(BaseInspection inspection,
                                                                   InspectionManager inspectionManager,
                                                                   boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method){
            final PsiParameterList parameterList = method.getParameterList();
            if(parameterList == null){
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if(parameters == null || parameters.length == 0){
                return;
            }
            final PsiMethod[] superMethods =
                    PsiSuperMethodUtil.findSuperMethods(method);
            if(superMethods == null || superMethods.length == 0){
                return;
            }
            for(int i = 0; i < parameters.length; i++){
                checkParameter(parameters[i], i, superMethods);
            }
        }

        private void checkParameter(PsiParameter parameter, int index,
                                    PsiMethod[] superMethods){
            final String parameterName = parameter.getName();
            if(parameterName == null){
                return;
            }
            for(int i = 0; i < superMethods.length; i++){
                final PsiMethod superMethod = superMethods[i];
                final PsiParameterList parameterList =
                        superMethod.getParameterList();
                if(parameterList != null){
                    final PsiParameter[] parameters =
                            parameterList.getParameters();
                    if(parameters != null){
                        final String superParameterName =
                                parameters[index].getName();
                        if(superParameterName != null &&
                                   !superParameterName.equals(parameterName)){
                            if(!m_ignoreSingleCharacterNames ||
                                       superParameterName.length() != 1){
                                registerVariableError(parameter);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }
}
