package com.siyeh.ig.jdk;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import org.jetbrains.annotations.NotNull;

public class EnumClassInspection extends ClassInspection {

    public String getDisplayName() {
        return "Enumerated class";
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Enumerated class '#ref' #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EnumClassVisitor();
    }

    private static class EnumClassVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            if (!aClass.isEnum()) {
                return;
            }
            registerClassError(aClass);
        }

    }

}