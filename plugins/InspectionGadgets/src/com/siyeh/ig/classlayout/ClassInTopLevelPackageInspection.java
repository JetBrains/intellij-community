package com.siyeh.ig.classlayout;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MoveClassFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class ClassInTopLevelPackageInspection extends ClassInspection {
    private final MoveClassFix fix = new MoveClassFix();

    public String getID(){
        return "ClassWithoutPackageStatement";
    }
    public String getDisplayName() {
        return "Class without package statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Class #ref lacks a package statement #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ClassInTopLevelPackageVisitor();
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    private static class ClassInTopLevelPackageVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (ClassUtils.isInnerClass(aClass)) {
                return;
            }
            final PsiFile file = aClass.getContainingFile();

            if (file == null || !(file instanceof PsiJavaFile)) {
                return;
            }
            if (((PsiJavaFile) file).getPackageStatement() != null) {
                return;
            }
            registerClassError(aClass);
        }

    }

}
