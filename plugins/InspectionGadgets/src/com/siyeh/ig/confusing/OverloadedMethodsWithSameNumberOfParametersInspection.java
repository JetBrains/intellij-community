package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;

public class OverloadedMethodsWithSameNumberOfParametersInspection extends MethodInspection {

    public String getDisplayName() {
        return "Overloaded methods with same number of parameters";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Multiple methods names '#ref' with the same number of parameters";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new OverloadedMethodsWithSameNumberOfParametersVisitor(this, inspectionManager, onTheFly);
    }

    private static class OverloadedMethodsWithSameNumberOfParametersVisitor extends BaseInspectionVisitor {
        private OverloadedMethodsWithSameNumberOfParametersVisitor(BaseInspection inspection,
                                                                   InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            if (method.isConstructor()) {
                return;
            }

            final String methodName = method.getName();
            if (methodName == null) {
                return;
            }
            final int parameterCount = calculateParamCount(method);

            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            final PsiMethod[] methods = aClass.getMethods();
            for (int i = 0; i < methods.length; i++) {
                if (!methods[i].equals(method)) {
                    final String testMethName = methods[i].getName();

                    final int testParameterCount = calculateParamCount(methods[i]);
                    if (testMethName != null && methodName.equals(testMethName) &&
                            parameterCount == testParameterCount) {
                        registerMethodError(method);
                    }
                }
            }
        }

        private static int calculateParamCount(PsiMethod method) {
            final PsiParameterList paramList = method.getParameterList();
            final PsiParameter[] parameters = paramList.getParameters();
            return parameters.length;
        }
    }

}
