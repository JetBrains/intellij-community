package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TypeUtils {
    private TypeUtils() {
        super();
    }

    public static boolean expressionHasType(@NotNull String typeName,@Nullable PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        final PsiType type = expression.getType();
        return typeEquals(typeName, type);
    }

    public static boolean typeEquals(@NotNull String typeName, @Nullable PsiType targetType) {
        if (targetType == null || typeName == null) {
            return false;
        }
        return targetType.equalsToText(typeName);
    }

    public static boolean isJavaLangObject(@Nullable PsiType targetType) {
        return typeEquals("java.lang.Object", targetType);
    }

    public static boolean isJavaLangString(@Nullable PsiType targetType) {
        return typeEquals("java.lang.String", targetType);
    }

    public static boolean expressionHasTypeOrSubtype(@NotNull String typeName,
                                                     @Nullable PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        final PsiType type = expression.getType();
        if (type == null) {
            return false;
        }
        return typeInherits(type, typeName);
    }

    private static boolean typeInherits(@NotNull PsiType type,@NotNull String typeName) {
        if(type.equalsToText(typeName))
        {
            return true;
        }
        final PsiType[] superTypes = type.getSuperTypes();
        for(PsiType superType : superTypes){
            if(typeInherits(superType, typeName)){
                return true;
            }
        }
        return false;
    }
}
