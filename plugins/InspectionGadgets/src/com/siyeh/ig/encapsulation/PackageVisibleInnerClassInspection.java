package com.siyeh.ig.encapsulation;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MoveClassFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class PackageVisibleInnerClassInspection extends ClassInspection {
    private final MoveClassFix fix = new MoveClassFix();

    public String getDisplayName() {
        return "Package-visible inner class";
    }

    public String getGroupDisplayName() {
        return GroupNames.ENCAPSULATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Package-visible inner class #ref #loc";
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
            if (aClass.hasModifierProperty(PsiModifier.PUBLIC) ||
                    aClass.hasModifierProperty(PsiModifier.PROTECTED) ||
                    aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            if (!ClassUtils.isInnerClass(aClass)) {
                return;
            }
            registerClassError(aClass);
        }

    }

}
