package com.siyeh.ig.maturity;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.UtilityClassUtil;

public class ClassWithoutToStringInspection extends ClassInspection {

    public String getDisplayName() {
        return "Class without toString()";
    }

    public String getGroupDisplayName() {
        return GroupNames.MATURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Class #ref should probably implement .toString(), for debugging purposes";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ClassWithoutToStringVisitor(this, inspectionManager, onTheFly);
    }

    private static class ClassWithoutToStringVisitor extends BaseInspectionVisitor {
        private ClassWithoutToStringVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            //don't call super, to prevent drilldown
            if (aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum()) {
                return;
            }
            if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if (UtilityClassUtil.isUtilityClass(aClass)) {
                return;
            }
            final PsiMethod[] methods = aClass.getMethods();
            for (int i = 0; i < methods.length; i++) {
                final PsiMethod method = methods[i];
                final String methodName = method.getName();
                final PsiParameterList paramList = method.getParameterList();
                final PsiParameter[] parameters = paramList.getParameters();
                if ("toString".equals(methodName) && parameters.length == 0) {
                    return;
                }
            }
            registerClassError(aClass);
        }
    }

}
