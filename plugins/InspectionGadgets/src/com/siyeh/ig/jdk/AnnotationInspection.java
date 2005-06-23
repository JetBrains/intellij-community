package com.siyeh.ig.jdk;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;

public class AnnotationInspection extends ClassInspection {

    public String getDisplayName() {
        return "Annotation";
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK_GROUP_NAME;
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
