package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;

public class ClassNameDiffersFromFileNameInspection extends ClassInspection {

    public String getDisplayName() {
        return "Class name differs from file name";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Class name #ref differs from file name #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new classNameDiffersFromFileName(this, inspectionManager, onTheFly);
    }

    private static class classNameDiffersFromFileName extends BaseInspectionVisitor {

        private classNameDiffersFromFileName(BaseInspection inspection,
                                             InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (!(aClass.getParent() instanceof PsiJavaFile)) {
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) aClass.getParent();
            final String className = aClass.getName();
            if (className == null) {
                return;
            }
            final String fileName = file.getName();
            if (fileName == null) {
                return;
            }
            final int prefixIndex = fileName.indexOf((int) '.');
            final String filenameWithoutPrefix = fileName.substring(0, prefixIndex);
            if (className.equals(filenameWithoutPrefix)) {
                return;
            }
            registerClassError(aClass);
        }

    }

}
