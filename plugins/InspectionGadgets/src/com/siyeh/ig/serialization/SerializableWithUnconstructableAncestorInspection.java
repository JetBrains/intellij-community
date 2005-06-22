package com.siyeh.ig.serialization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

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
        assert aClass != null;
        PsiClass ancestor = aClass.getSuperClass();
        while (SerializationUtils.isSerializable(ancestor)) {
            assert ancestor != null;
            ancestor = ancestor.getSuperClass();
        }
        assert ancestor != null;
        return "#ref has an non-serializable ancestor " + ancestor.getName() + " without a no-arg constructor #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SerializableWithUnconstructableAncestorVisitor();
    }

    private static class SerializableWithUnconstructableAncestorVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down

            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (!SerializationUtils.isSerializable(aClass)) {
                return;
            }
            PsiClass ancestor = aClass.getSuperClass();
            final Set<PsiClass> visitedClasses = new HashSet<PsiClass>(16);
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
            for(final PsiMethod method : methods){
                if(method.isConstructor()){
                    hasConstructor = true;
                    final PsiParameterList params = method.getParameterList();
                    if(params != null){
                        if(params.getParameters().length == 0
                                &&
                                (method
                                        .hasModifierProperty(PsiModifier.PUBLIC) ||
                                        method
                                                .hasModifierProperty(PsiModifier.PROTECTED))){
                            hasNoArgConstructor = true;
                        }
                    }
                }
            }
            return hasNoArgConstructor || !hasConstructor;
        }
    }
}