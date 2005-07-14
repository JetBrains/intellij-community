package com.siyeh.ig.junit;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TestUtils;
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
            if(!TestUtils.isJUnitTestMethod(method))
            {
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
