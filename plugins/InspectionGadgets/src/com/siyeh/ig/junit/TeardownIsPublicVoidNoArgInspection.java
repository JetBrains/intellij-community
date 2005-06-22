package com.siyeh.ig.junit;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class TeardownIsPublicVoidNoArgInspection extends MethodInspection {
    public String getID(){
        return "TearDownWithIncorrectSignature";
    }
    public String getDisplayName() {
        return "'tearDown()' with incorrect signature";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() has incorrect signature";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TeardownIsPublicVoidNoArgVisitor();
    }

    private static class TeardownIsPublicVoidNoArgVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            //note: no call to super;
            final String methodName = method.getName();
            if (!"tearDown".equals(methodName)) {
                return;
            }
            final PsiType returnType = method.getReturnType();
            if (returnType == null) {
                return;
            }
            final PsiClass targetClass = method.getContainingClass();
            if(targetClass == null)
            {
                return;
            }
            if (!ClassUtils.isSubclass(targetClass, "junit.framework.TestCase")) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList == null) {
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters == null) {
                return;
            }
            if (parameters.length != 0) {
                registerMethodError(method);
            } else if (!returnType.equals(PsiType.VOID)) {
                registerMethodError(method);
            } else if (!method.hasModifierProperty(PsiModifier.PUBLIC) &&
                    !method.hasModifierProperty(PsiModifier.PROTECTED)) {
                registerMethodError(method);
            }
        }

    }

}
