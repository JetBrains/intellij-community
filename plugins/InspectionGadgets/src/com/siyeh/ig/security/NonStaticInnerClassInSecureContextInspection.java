package com.siyeh.ig.security;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class NonStaticInnerClassInSecureContextInspection extends ClassInspection {

    public String getDisplayName() {
        return "Non-static inner class in secure context";
    }

    public String getGroupDisplayName() {
        return GroupNames.SECURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Non-static inner class #ref, compromising security #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NonStaticInnerClassInSecureContextVisitor();
    }

    private static class NonStaticInnerClassInSecureContextVisitor extends BaseInspectionVisitor {
    
        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            if (!ClassUtils.isInnerClass(aClass)) {
                return;
            }
            registerClassError(aClass);
        }
    }
}
