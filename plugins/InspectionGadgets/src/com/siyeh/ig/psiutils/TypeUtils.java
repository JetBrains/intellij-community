package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;

public class TypeUtils {
    private TypeUtils() {
        super();
    }

    public static boolean expressionHasType(String typeName, PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        final PsiType type = expression.getType();
        return typeEquals(typeName, type);
    }

    public static boolean typeEquals(String typeName, PsiType targetType) {
        if (targetType == null) {
            return false;
        }
        return targetType.equalsToText(typeName);
    }

    public static boolean isJavaLangObject(PsiType targetType) {
        return typeEquals("java.lang.Object", targetType);
    }

    public static boolean isJavaLangString(PsiType targetType) {
        return typeEquals("java.lang.String", targetType);
    }

    public static boolean expressionHasTypeOrSubtype(String typeName, PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        final PsiType type = expression.getType();
        if (type == null) {
            return false;
        }
        return typeInherits(type, typeName);
    }

    private static boolean typeInherits(PsiType type, String typeName) {
        final String text = type.getCanonicalText();
        if(typeName.equals(text))
        {
            return true;
        }
        final PsiType[] superTypes = type.getSuperTypes();
        for (int i = 0; i < superTypes.length; i++) {
            if (typeInherits(superTypes[i], typeName)) {
                return true;
            }
        }
        return false;
    }
}
