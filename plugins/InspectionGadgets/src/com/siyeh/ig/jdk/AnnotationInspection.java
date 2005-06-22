package com.siyeh.ig.jdk;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;

public class AnnotationInspection extends BaseInspection {

    public String getDisplayName() {
        return "Annotation";
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK_GROUP_NAME;
    }

    public ProblemDescriptor[] doCheckClass(PsiClass aClass,
                                            InspectionManager manager,
                                            boolean isOnTheFly) {
        if (!aClass.isPhysical()) {
            return super.doCheckClass(aClass, manager, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(manager, isOnTheFly);
        aClass.accept(visitor);

        return visitor.getErrors();
    }

    public ProblemDescriptor[] doCheckMethod(PsiMethod method,
                                             InspectionManager manager,
                                             boolean isOnTheFly) {
        if (!method.isPhysical()) {
            return super.doCheckMethod(method, manager, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(manager, isOnTheFly);
        method.accept(visitor);
        return visitor.getErrors();
    }

    public ProblemDescriptor[] doCheckField(PsiField field,
                                            InspectionManager manager,
                                            boolean isOnTheFly) {
        if (!field.isPhysical()) {
            return super.doCheckField(field, manager, isOnTheFly);
        }
        final BaseInspectionVisitor visitor = createVisitor(manager, isOnTheFly);
        field.accept(visitor);
        return visitor.getErrors();
    }

    public String buildErrorString(PsiElement location) {
        return "Annotation '#ref' #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryInterfaceModifierVisitor();
    }

    private static class UnnecessaryInterfaceModifierVisitor extends BaseInspectionVisitor {


        public void visitAnnotation(PsiAnnotation annotation) {
            super.visitAnnotation(annotation);
            registerError(annotation);
        }
    }
}
