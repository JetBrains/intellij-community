package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.MoveClassFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class ProtectedInnerClassInspection extends ClassInspection {
    private final MoveClassFix fix = new MoveClassFix();

    public String getDisplayName() {
        return "Protected inner class";
    }

    public String getGroupDisplayName() {
        return GroupNames.ENCAPSULATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Protected inner class #ref #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new PackageVisibleInnerClassVisitor();
    }

    private static class PackageVisibleInnerClassVisitor extends BaseInspectionVisitor {


        public void visitClass(@NotNull PsiClass aClass) {
            if (!aClass.hasModifierProperty(PsiModifier.PROTECTED)) {
                return;
            }
            if (!ClassUtils.isInnerClass(aClass)) {
                return;
            }
            registerClassError(aClass);
        }

    }

}
