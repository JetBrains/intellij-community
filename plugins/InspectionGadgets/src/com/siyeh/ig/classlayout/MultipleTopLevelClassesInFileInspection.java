package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.MoveClassFix;

public class MultipleTopLevelClassesInFileInspection extends ClassInspection {
    private final MoveClassFix fix = new MoveClassFix();

    public String getDisplayName() {
        return "Multiple top level classes in single file";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Multiple top level classes in file";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new MultipleTopLevelClassesInFileVisitor(this, inspectionManager, onTheFly);
    }

    private static class MultipleTopLevelClassesInFileVisitor extends BaseInspectionVisitor {

        private MultipleTopLevelClassesInFileVisitor(BaseInspection inspection,
                                                     InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (!(aClass.getParent() instanceof PsiJavaFile)) {
                return;
            }
            int numClasses = 0;
            final PsiJavaFile file = (PsiJavaFile) aClass.getParent();
            final PsiElement[] children = file.getChildren();
            for (int i = 0; i < children.length; i++) {
                final PsiElement child = children[i];
                if (child instanceof PsiClass) {
                    numClasses++;
                }
            }
            if (numClasses <= 1) {
                return;
            }
            registerClassError(aClass);
        }

    }

}
