package com.siyeh.ig.security;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class CustomClassloaderInspection extends ClassInspection {

    public String getDisplayName() {
        return "Custom ClassLoader";
    }

    public String getGroupDisplayName() {
        return GroupNames.SECURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Custom ClassLoader class '#ref' #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CustomClassloaderVisitor();
    }

    private static class CustomClassloaderVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass){
            if(!ClassUtils.isSubclass(aClass, "java.lang.ClassLoader")) {
                return;
            }
            if("java.lang.ClassLoader".equals(aClass.getQualifiedName())) {
                return;
            }
            registerClassError(aClass);
        }
    }

}
