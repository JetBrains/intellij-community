package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class FeatureEnvyInspection extends MethodInspection {

    public String getDisplayName() {
        return "Feature Envy";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    public String buildErrorString(Object arg) {
        final String className = ((PsiNamedElement) arg).getName();
        return "Class " + className + " accessed repeatedly in method #ref #loc";
    }


    public BaseInspectionVisitor buildVisitor() {
        return new FeatureEnvyVisitor();
    }

    private static class FeatureEnvyVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            final PsiIdentifier nameIdentifier = method.getNameIdentifier();
            if (nameIdentifier == null) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            final ClassAccessVisitor visitor = new ClassAccessVisitor(containingClass);
            method.accept(visitor);
            final Set<PsiClass> overaccessedClasses = visitor.getOveraccessedClasses();
            for(PsiClass aClass : overaccessedClasses){
                registerError(nameIdentifier, aClass);
            }
        }

    }

}
