package com.siyeh.ig.junit;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class StaticSuiteInspection extends MethodInspection {
    public String getID(){
        return "SuiteNotDeclaredStatic";
    }
    public String getDisplayName() {
        return "'suite()' method not declared 'static'";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "JUnit #ref() methods not declared 'static' #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StaticSuiteVisitor();
    }

    private static class StaticSuiteVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            //note: no call to super
            final String methodName = method.getName();
            if(!"suite".equals(methodName)){
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            if (!ClassUtils.isSubclass(aClass, "junit.framework.TestCase")) {
                return;
            }

            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList == null) {
                return;
            }
            if (parameterList.getParameters().length != 0) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            registerMethodError(method);
        }

    }

}
