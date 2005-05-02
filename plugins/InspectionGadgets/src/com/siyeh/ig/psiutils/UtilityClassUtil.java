package com.siyeh.ig.psiutils;

import com.intellij.psi.*;

public class UtilityClassUtil {
    private UtilityClassUtil() {
        super();
    }

    public static boolean isUtilityClass(PsiClass aClass) {
        if (aClass.isInterface() || aClass.isEnum() || aClass.isAnnotationType()) {
            return false;
        }
        final PsiReferenceList extendsList = aClass.getExtendsList();
        if (extendsList != null
                && extendsList.getReferenceElements().length > 0) {
            return false;
        }
        final PsiReferenceList implementsList = aClass.getImplementsList();
        if (implementsList != null
                && implementsList.getReferenceElements().length > 0) {
            return false;
        }
        if (!allMethodsStatic(aClass)) {
            return false;
        }
        if (!allFieldsStatic(aClass)) {
            return false;
        }
        return !(aClass.getMethods().length == 0 &&
                aClass.getFields().length == 0);
    }

    private static boolean allFieldsStatic(PsiClass aClass) {
        boolean allFieldsStatic = true;
        final PsiField[] fields = aClass.getFields();
        for(final PsiField field : fields){
            if(!field.hasModifierProperty(PsiModifier.STATIC)){
                allFieldsStatic = false;
            }
        }
        return allFieldsStatic;
    }

    private static boolean allMethodsStatic(PsiClass aClass) {
        final PsiMethod[] methods = aClass.getMethods();
        for(final PsiMethod method : methods){
            if(!(method.isConstructor() ||
                    method.hasModifierProperty(PsiModifier.STATIC))){
                return false;
            }
        }
        return true;
    }
}
