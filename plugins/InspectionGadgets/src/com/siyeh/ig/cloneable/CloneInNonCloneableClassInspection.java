package com.siyeh.ig.cloneable;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.MakeCloneableFix;
import com.siyeh.ig.psiutils.CloneUtils;
import org.jetbrains.annotations.NotNull;

public class CloneInNonCloneableClassInspection extends MethodInspection {

    private InspectionGadgetsFix fix = new MakeCloneableFix();
    public String getDisplayName() {
        return "'clone()' method in non-Cloneable class";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLONEABLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() defined in non-Cloneable class #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new CloneInNonCloneableClassVisitor(this, inspectionManager, onTheFly);
    }

    private static class CloneInNonCloneableClassVisitor extends BaseInspectionVisitor {
        private CloneInNonCloneableClassVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(@NotNull PsiMethod method){
            final PsiClass containingClass = method.getContainingClass();
            final String name = method.getName();
            if(!"clone".equals(name))
            {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if(parameterList == null)
            {
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if(parameters == null || parameters.length!=0)
            {
                return;
            }
            if(containingClass == null)
            {
                return;
            }
            if(CloneUtils.isCloneable(containingClass)){
                return;
            }
            registerMethodError(method);
        }

    }

}
