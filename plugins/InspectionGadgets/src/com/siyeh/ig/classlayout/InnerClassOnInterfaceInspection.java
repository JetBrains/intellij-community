package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.MoveClassFix;
import com.siyeh.ig.psiutils.ClassUtils;

public class InnerClassOnInterfaceInspection extends ClassInspection {
    private final MoveClassFix fix = new MoveClassFix();

    public String getDisplayName() {
        return "Inner class of interface";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiClass innerClass = (PsiClass) location.getParent();
        final PsiClass parentInterface =
                ClassUtils.getContainingClass(innerClass);
        final String interfaceName = parentInterface.getName();
        return "Interface " + interfaceName + " has inner class #ref #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new InnerClassOnInterfaceVisitor(this, inspectionManager, onTheFly);
    }

    private static class InnerClassOnInterfaceVisitor extends BaseInspectionVisitor {
        private InnerClassOnInterfaceVisitor(BaseInspection inspection,
                                             InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (!aClass.isInterface()||aClass.isAnnotationType()) {
                return;
            }
            final PsiClass[] innerClasses = aClass.getInnerClasses();
            for (int i = 0; i < innerClasses.length; i++) {
                if (!innerClasses[i].isEnum() && !innerClasses[i].isAnnotationType()) {
                    registerClassError(innerClasses[i]);
                }
            }
        }


    }

}
