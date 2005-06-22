package com.siyeh.ig.junit;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class TestCaseWithNoTestMethodsInspection extends ClassInspection {
    public String getID(){
        return "JUnitTestCaseWithNoTests";
    }

    public String getDisplayName() {
        return "JUnit test case with no tests";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "JUnit test case #ref has no tests";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TestCaseWithNoTestMethodsVisitor();
    }

    private static class TestCaseWithNoTestMethodsVisitor extends BaseInspectionVisitor {
      

        public void visitClass(@NotNull PsiClass aClass) {
            super.visitClass(aClass);
            if (aClass.isInterface()
                    || aClass.isEnum()
                    || aClass.isAnnotationType()
                    || aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if(aClass instanceof PsiTypeParameter ||
                    aClass instanceof PsiAnonymousClass){
                return;
            }
            final PsiMethod[] methods = aClass.getMethods();
            for(final PsiMethod method : methods){
                if(isTest(method)){
                    return;
                }
            }
            if(!ClassUtils.isSubclass(aClass, "junit.framework.TestCase")){
                return;
            }
            registerClassError(aClass);
        }

        private boolean isTest(PsiMethod method) {
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                return false;
            }
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
                return false;
            }

            final PsiType returnType = method.getReturnType();
            if (!PsiType.VOID.equals(returnType)) {
                return false;
            }
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            if(parameters.length != 0){
                return false;
            }
            final String name = method.getName();
            return name.startsWith("test");

        }

    }

}
