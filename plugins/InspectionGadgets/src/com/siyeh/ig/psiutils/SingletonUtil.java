package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class SingletonUtil {
    private SingletonUtil() {
        super();
    }

    public static boolean isSingleton(@NotNull PsiClass aClass) {
        if (aClass.isInterface() || aClass.isEnum() || aClass.isAnnotationType()) {
            return false;
        }
        if(aClass instanceof PsiTypeParameter ||
                aClass instanceof PsiAnonymousClass){
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
        for(final PsiField field : fields){
            final String className = aClass.getQualifiedName();
            if(field.hasModifierProperty(PsiModifier.STATIC)){
                final PsiType type = field.getType();
                if(type != null){
                    final String fieldTypeName = type.getCanonicalText();
                    if(fieldTypeName.equals(className)){
                        numSelfInstances++;
                    }
                }
            }
        }
        return numSelfInstances == 1;
    }

    private static boolean hasConstructor(PsiClass aClass) {
        return aClass.getConstructors().length>0;
    }

    private static boolean hasVisibleConstructor(PsiClass aClass) {
        final PsiMethod[] methods = aClass.getConstructors();
        for(final PsiMethod method : methods){
            if(method.hasModifierProperty(PsiModifier.PUBLIC)){
                return true;
            }
            if(!method.hasModifierProperty(PsiModifier.PRIVATE) &&
                    !method.hasModifierProperty(PsiModifier.PROTECTED)){
                return true;
            }
        }
        return false;
    }
}
