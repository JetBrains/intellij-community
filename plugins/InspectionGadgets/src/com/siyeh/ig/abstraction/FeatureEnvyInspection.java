package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;

import java.util.Iterator;
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


    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new InstanceVariableRepeatedlyAccessedVisitor(this, inspectionManager, onTheFly);
    }

    private static class InstanceVariableRepeatedlyAccessedVisitor extends BaseInspectionVisitor {
        private InstanceVariableRepeatedlyAccessedVisitor(BaseInspection inspection,
                                                          InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            final PsiIdentifier nameIdentifier = method.getNameIdentifier();
            if (nameIdentifier == null) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            final ClassAccessVisitor visitor = new ClassAccessVisitor(containingClass);
            method.accept(visitor);
            final Set overaccessedClasses = visitor.getOveraccessedClasses();
            for (Iterator iterator = overaccessedClasses.iterator(); iterator.hasNext();) {
                final PsiClass aClass = (PsiClass) iterator.next();
                registerError(nameIdentifier, aClass);
            }
        }

    }

}
