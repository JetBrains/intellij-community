package com.siyeh.ig.psiutils;

import com.intellij.psi.*;

public class SingletonUtil {
    private SingletonUtil() {
        super();
    }

    public static boolean isSingleton(PsiClass aClass) {
        if (aClass.isInterface() || aClass.isEnum() || aClass.isAnnotationType()) {
            return false;
        }
        if (!hasConstructor(aClass)) {
            return false;
        }
        if (hasVisibleConstructor(aClass)) {
            return false;
        }
        return containsOneStaticSelfInstance(aClass);
    }

    private static boolean containsOneStaticSelfInstance(PsiClass aClass) {
        final PsiField[] fields = aClass.getFields();
        int numSelfInstances = 0;
        for (int i = 0; i < fields.length; i++) {
            final PsiField field = fields[i];
            final String className = aClass.getQualifiedName();
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                final PsiType type = field.getType();
                if (type != null) {
                    final String fieldTypeName = type.getCanonicalText();
                    if (fieldTypeName.equals(className)) {
                        numSelfInstances++;
                    }
                }
            }
        }
        return numSelfInstances == 1;
    }

    private static boolean hasConstructor(PsiClass aClass) {
        final PsiMethod[] methods = aClass.getMethods();
        for (int i = 0; i < methods.length; i++) {
            final PsiMethod method = methods[i];
            if (method.isConstructor()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasVisibleConstructor(PsiClass aClass) {
        final PsiMethod[] methods = aClass.getMethods();
        for (int i = 0; i < methods.length; i++) {
            final PsiMethod method = methods[i];
            if (method.isConstructor()) {
                if (method.hasModifierProperty(PsiModifier.PUBLIC)) {
                    return true;
                }
                if (!method.hasModifierProperty(PsiModifier.PRIVATE) &&
                        !method.hasModifierProperty(PsiModifier.PROTECTED)) {
                    return true;
                }
            }
        }
        return false;
    }
}
