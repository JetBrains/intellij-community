package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.TypeUtils;

public class CovariantCompareToInspection extends MethodInspection {
    private static final String COMPARE_TO_METHOD_NAME = "compareTo";

    public String getDisplayName() {
        return "Covariant compareTo()";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref should take Object as it's argument #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new CovariantCompareToVisitor(this, inspectionManager, onTheFly);
    }

    private static class CovariantCompareToVisitor extends BaseInspectionVisitor {

        private CovariantCompareToVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            // note: no call to super
            final String name = method.getName();
            if (!COMPARE_TO_METHOD_NAME.equals(name)) {
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
            for (int i = 0; i < methods.length; i++) {
                if (isNonVariantCompareTo(methods[i])) {
                    return;
                }
            }
            final PsiClassType[] implementsListTypes = aClass.getImplementsListTypes();
            for (int i = 0; i < implementsListTypes.length; i++) {
                final PsiClassType implementedType = implementsListTypes[i];
                final String implementedClassName = implementedType.getClassName();
                if (("java.lang.Comparable".equals(implementedClassName) ||
                        "Comparable".equals(implementedClassName)
                        )
                        && implementedType.hasParameters()) {
                    return;
                }
            }
            registerMethodError(method);
        }

        private static boolean isNonVariantCompareTo(PsiMethod method) {
            final String methodName = method.getName();
            if (!COMPARE_TO_METHOD_NAME.equals(methodName)) {
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
