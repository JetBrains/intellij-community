package com.siyeh.ig.confusing;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class OverloadedMethodsWithSameNumberOfParametersInspection extends MethodInspection {

    public String getDisplayName() {
        return "Overloaded methods with same number of parameters";
    }

    public String getGroupDisplayName() {
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Multiple methods names '#ref' with the same number of parameters";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new OverloadedMethodsWithSameNumberOfParametersVisitor();
    }

    private static class OverloadedMethodsWithSameNumberOfParametersVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
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
            for(PsiMethod method1 : methods){
                if(!method1.equals(method)){
                    final String testMethName = method1.getName();

                    final int testParameterCount = calculateParamCount(method1);
                    if(testMethName != null && methodName
                            .equals(testMethName) &&
                            parameterCount == testParameterCount){
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
