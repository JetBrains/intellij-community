package com.siyeh.ig.jdk;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;

public class AnnotationInspection extends BaseInspection {

    public String getDisplayName() {
        return "Annotation";
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK_GROUP_NAME;
    }

    public ProblemDescriptor[] checkClass(PsiClass aClass, InspectionManager mgr, boolean isOnTheFly) {
        if (!aClass.isPhysical()) {
            return super.checkClass(aClass, mgr, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
        aClass.accept(visitor);

        return visitor.getErrors();
    }

    public ProblemDescriptor[] checkMethod(PsiMethod method, InspectionManager mgr, boolean isOnTheFly) {
        if (!method.isPhysical()) {
            return super.checkMethod(method, mgr, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
        method.accept(visitor);
        return visitor.getErrors();
    }

    public ProblemDescriptor[] checkField(PsiField field, InspectionManager mgr, boolean isOnTheFly) {
        if (!field.isPhysical()) {
            return super.checkField(field, mgr, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(mgr, isOnTheFly);
        field.accept(visitor);
        return visitor.getErrors();
    }

    public String buildErrorString(PsiElement location) {
        return "Annotation '#ref' #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnnecessaryInterfaceModifierVisitor(this, inspectionManager, onTheFly);
    }

    private static class UnnecessaryInterfaceModifierVisitor extends BaseInspectionVisitor {

        private UnnecessaryInterfaceModifierVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitAnnotation(PsiAnnotation annotation) {
            super.visitAnnotation(annotation);
            registerError(annotation);
        }
    }
}
