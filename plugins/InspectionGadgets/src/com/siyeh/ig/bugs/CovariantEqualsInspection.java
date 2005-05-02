package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.TypeUtils;

public class CovariantEqualsInspection extends MethodInspection {

    public String getDisplayName() {
        return "Covariant 'equals()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref should take Object as its argument #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new CovariantEqualsVisitor(this, inspectionManager, onTheFly);
    }

    private static class CovariantEqualsVisitor extends BaseInspectionVisitor {
        private static final String EQUALS_METHOD_NAME = "equals";

        private CovariantEqualsVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            // note: no call to super
            final String name = method.getName();
            if (!EQUALS_METHOD_NAME.equals(name)) {
                return;
            }
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            final PsiParameterList paramList = method.getParameterList();
            if (paramList == null) {
                return;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            if (parameters.length != 1) {
                return;
            }
            final PsiType argType = parameters[0].getType();
            if (TypeUtils.isJavaLangObject(argType)) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            final PsiMethod[] methods = aClass.getMethods();
            for(PsiMethod method1 : methods){
                if(isNonVariantEquals(method1)){
                    return;
                }
            }
            registerMethodError(method);

        }

        private static boolean isNonVariantEquals(PsiMethod method) {
            final String name = method.getName();
            if (!EQUALS_METHOD_NAME.equals(name)) {
                return false;
            }
            final PsiParameterList paramList = method.getParameterList();
            if (paramList == null) {
                return false;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            if (parameters.length != 1) {
                return false;
            }
            final PsiType argType = parameters[0].getType();
            return TypeUtils.isJavaLangObject(argType);
        }

    }

}
