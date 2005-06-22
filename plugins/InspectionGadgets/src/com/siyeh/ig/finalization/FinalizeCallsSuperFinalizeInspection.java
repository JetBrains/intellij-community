package com.siyeh.ig.finalization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FinalizeCallsSuperFinalizeInspection extends MethodInspection{
    @SuppressWarnings("PublicField")
    public boolean m_ignoreForObjectSubclasses = false;

    public String getID(){
        return "FinalizeDoesntCallSuperFinalize";
    }

    public String getDisplayName(){
        return "'finalize()' doesn't call 'super.finalize()'";
    }

    public String getGroupDisplayName(){
        return GroupNames.FINALIZATION_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel("Ignore for direct subclasses of java.lang.Object",
                                              this,
                                              "m_ignoreForObjectSubclasses");
    }

    public String buildErrorString(PsiElement location){
        return "#ref() doesn't call super.finalize()";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new NoExplicitFinalizeCallsVisitor();
    }

    private class NoExplicitFinalizeCallsVisitor extends BaseInspectionVisitor{
        public void visitMethod(@NotNull PsiMethod method){
            //note: no call to super;
            final String methodName = method.getName();
            if(!"finalize".equals(methodName)){
                return;
            }
            if(method.hasModifierProperty(PsiModifier.NATIVE) ||
                    method.hasModifierProperty(PsiModifier.ABSTRACT))
            {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null){
                return;
            }
            if(m_ignoreForObjectSubclasses){
                final PsiClass superClass = containingClass.getSuperClass();
                if(superClass != null){
                    final String superClassName = superClass.getQualifiedName();
                    if("java.lang.Object".equals(superClassName)){
                        return;
                    }
                }
            }
            final PsiParameterList parameterList = method.getParameterList();
            if(parameterList.getParameters().length != 0){
                return;
            }
            final CallToSuperFinalizeVisitor visitor = new CallToSuperFinalizeVisitor();
            method.accept(visitor);
            if(visitor.isCallToSuperFinalizeFound()){
                return;
            }
            registerMethodError(method);
        }
    }
}
