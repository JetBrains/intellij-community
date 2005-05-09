package com.siyeh.ig.abstraction;

import com.intellij.psi.*;
import com.siyeh.ig.psiutils.LibraryUtil;

class ConcreteClassUtil {
    private ConcreteClassUtil() {
        super();
    }

    public static boolean typeIsConcreteClass(PsiTypeElement typeElement) {
        if (typeElement == null) {
            return false;
        }
        final PsiType type = typeElement.getType();
        if (type == null) {
            return false;
        }
        final PsiType baseType = type.getDeepComponentType();
        if (baseType == null) {
            return false;
        }
        if (!(baseType instanceof PsiClassType)) {
            return false;
        }
        final PsiClass aClass = ((PsiClassType) baseType).resolve();
        if (aClass == null) {
            return false;
        }
        if (aClass.isInterface() || aClass.isEnum()|| aClass.isAnnotationType()) {
            return false;
        }
        if(aClass instanceof PsiTypeParameter)
        {
            return false;
        }
        return !LibraryUtil.classIsInLibrary(aClass);
    }
}
