package com.siyeh.ig.bugs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

public class MisspelledHashcodeInspection extends MethodInspection{
    private final RenameFix fix = new RenameFix("hashCode");

    public String getDisplayName(){
        return "'hashcode()' instead of 'hashCode()'";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    public String buildErrorString(PsiElement location){
        return "#ref() should probably be hashCode() #loc";
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new MisspelledHashcodeVisitor();
    }

    private static class MisspelledHashcodeVisitor
                                                   extends BaseInspectionVisitor{


        public void visitMethod(@NotNull PsiMethod method){
            //note: no call to super
            final String methodName = method.getName();
            if(!"hashcode".equals(methodName)){
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if(parameterList == null){
                return;
            }
            if(parameterList.getParameters().length != 0){
                return;
            }
            registerMethodError(method);
        }
    }
}
