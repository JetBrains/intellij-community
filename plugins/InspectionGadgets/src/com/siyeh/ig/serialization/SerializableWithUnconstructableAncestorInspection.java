package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.SerializationUtils;

import java.util.HashSet;
import java.util.Set;

public class SerializableWithUnconstructableAncestorInspection extends ClassInspection {
    public String getID(){
        return "SerializableClassWithUnconstructableAncestor";
    }
    public String getDisplayName() {
        return "Serializable class with unconstructable ancestor";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiClass aClass = (PsiClass) location.getParent();
        PsiClass ancestor = aClass.getSuperClass();
        while (SerializationUtils.isSerializable(ancestor)) {
            ancestor = ancestor.getSuperClass();
        }
        return "#ref has an non-serializable ancestor " + ancestor.getName() + " without a no-arg constructor #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SerializableWithUnconstructableAncestorVisitor(this, inspectionManager, onTheFly);
    }

    private static class SerializableWithUnconstructableAncestorVisitor extends BaseInspectionVisitor {
        private SerializableWithUnconstructableAncestorVisitor(BaseInspection inspection,
                                                               InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so it doesn't drill down

            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (!SerializationUtils.isSerializable(aClass)) {
                return;
            }
            PsiClass ancestor = aClass.getSuperClass();
            final Set visitedClasses = new HashSet(16);
            while (ancestor != null && SerializationUtils.isSerializable(ancestor)) {
                ancestor = ancestor.getSuperClass();
                if (!visitedClasses.add(ancestor)) {
                    return;
                }
            }
            if (ancestor == null) {
                return;  // can't happen, since Object isn't serializable,
                //// but I don't trust the PSI as far as I can throw it
            }
            if (classHasNoArgConstructor(ancestor)) {
                return;
            }
            registerClassError(aClass);
        }

        private static boolean classHasNoArgConstructor(PsiClass ancestor) {
            boolean hasConstructor = false;
            boolean hasNoArgConstructor = false;
            final PsiMethod[] methods = ancestor.getMethods();
            for (int i = 0; i < methods.length; i++) {
                final PsiMethod method = methods[i];
                if (method.isConstructor()) {
                    hasConstructor = true;
                    final PsiParameterList params = method.getParameterList();
                    if (params != null) {
                        if (params.getParameters().length == 0
                                && method.hasModifierProperty(PsiModifier.PUBLIC)) {
                            hasNoArgConstructor = true;
                        }
                    }
                }

            }
            return hasNoArgConstructor || !hasConstructor;
        }
    }
}