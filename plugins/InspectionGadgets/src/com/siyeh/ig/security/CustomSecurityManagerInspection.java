package com.siyeh.ig.security;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class CustomSecurityManagerInspection extends ClassInspection {

    public String getDisplayName() {
        return "Custom SecurityManager";
    }

    public String getGroupDisplayName() {
        return GroupNames.SECURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Custom SecurityManager class '#ref' #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CustomSecurityManagerVisitor();
    }

    private static class CustomSecurityManagerVisitor extends BaseInspectionVisitor {
        public void visitClass(@NotNull PsiClass aClass){
            if(!ClassUtils.isSubclass(aClass, "java.lang.SecurityManager")) {
                return;
            }
            if("java.lang.SecurityManager".equals(aClass.getQualifiedName())) {
                return;
            }
            registerClassError(aClass);
        }
    }

}
