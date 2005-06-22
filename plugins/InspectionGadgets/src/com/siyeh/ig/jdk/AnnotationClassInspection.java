package com.siyeh.ig.jdk;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import org.jetbrains.annotations.NotNull;

public class AnnotationClassInspection extends ClassInspection {

    public String getDisplayName() {
        return "Annotation class";
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Annotation class '#ref' #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AnnotationClassVisitor();
    }

    private static class AnnotationClassVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            if (!aClass.isAnnotationType()) {
                return;
            }
            registerClassError(aClass);
        }

    }

}