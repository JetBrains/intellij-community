package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.MoveClassFix;
import com.siyeh.ig.psiutils.ClassUtils;

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

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ClassInTopLevelPackageVisitor(this, inspectionManager, onTheFly);
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    private static class ClassInTopLevelPackageVisitor extends BaseInspectionVisitor {
        private ClassInTopLevelPackageVisitor(BaseInspection inspection,
                                              InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
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
