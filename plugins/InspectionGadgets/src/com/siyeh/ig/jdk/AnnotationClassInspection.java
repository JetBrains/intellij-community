package com.siyeh.ig.jdk;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;

public class AnnotationClassInspection extends ClassInspection {

    public String getDisplayName() {
        return "Annotation class";
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Annotation class '#ref' #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new AnnotationClassVisitor(this, inspectionManager, onTheFly);
    }

    private static class AnnotationClassVisitor extends BaseInspectionVisitor {
        private AnnotationClassVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            if (!aClass.isAnnotationType()) {
                return;
            }
            registerClassError(aClass);
        }

    }

}