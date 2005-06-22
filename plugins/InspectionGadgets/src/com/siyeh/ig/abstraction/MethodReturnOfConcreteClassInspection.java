package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class MethodReturnOfConcreteClassInspection extends MethodInspection {

    public String getDisplayName() {
        return "Method return of concrete class";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    protected String buildErrorString(PsiElement location) {
        return "Method returns a concrete class #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MethodReturnOfConcreteClassVisitor();
    }

    private static class MethodReturnOfConcreteClassVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            if (method.isConstructor()) {
                return;
            }
            final PsiTypeElement typeElement = method.getReturnTypeElement();
            if (!ConcreteClassUtil.typeIsConcreteClass(typeElement)) {
                return;
            }
            registerError(typeElement);
        }

    }

}
