package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class UncheckedExceptionClassInspection extends ClassInspection {

    public String getDisplayName() {
        return "Unchecked exception class";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Unchecked exception class #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UncheckedExceptionClassVisitor();
    }

    private static class UncheckedExceptionClassVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            if (!ClassUtils.isSubclass(aClass, "java.lang.Throwable")) {
                return;
            }
            if (ClassUtils.isSubclass(aClass, "java.lang.Exception") &&
                    !ClassUtils.isSubclass(aClass, "java.lang.RuntimeException")) {
                return;
            }
            registerClassError(aClass);
        }

    }

}
