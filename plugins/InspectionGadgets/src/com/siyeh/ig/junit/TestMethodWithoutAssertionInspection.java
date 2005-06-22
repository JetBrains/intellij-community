package com.siyeh.ig.junit;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class TestMethodWithoutAssertionInspection extends ExpressionInspection {
    public String getID(){
        return "JUnitTestMethodWithNoAssertions";
    }
    public String getDisplayName() {
        return "JUnit test method without any assertions";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "JUnit test method #ref() contains no assertions #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TestMethodWithoutAssertionVisitor();
    }

    private static class TestMethodWithoutAssertionVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            if(method.hasModifierProperty(PsiModifier.ABSTRACT) ||
                       !method.hasModifierProperty(PsiModifier.PUBLIC)){
                return;
            }

            final PsiType returnType = method.getReturnType();
            if (returnType == null) {
                return;
            }
            if(!returnType.equals(PsiType.VOID)){
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
                return;
            }
            final String methodName = method.getName();
            if(!methodName.startsWith("test")){
                return;
            }
            final PsiClass targetClass = method.getContainingClass();
            if (targetClass == null ||
                        !ClassUtils.isSubclass(targetClass, "junit.framework.TestCase")) {
                return;
            }
            final ContainsAssertionVisitor visitor = new ContainsAssertionVisitor();
            method.accept(visitor);
            if (visitor.containsAssertion()) {
                return;
            }
            registerMethodError(method);
        }



    }

}
