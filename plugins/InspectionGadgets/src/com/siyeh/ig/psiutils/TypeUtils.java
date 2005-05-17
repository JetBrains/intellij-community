package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiClass;
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
        if (targetType == null) {
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
        if (!(type instanceof PsiClassType)) {
            return false;
        }
        final PsiClassType classType = (PsiClassType) type;
        final PsiClass aClass = classType.resolve();
        if(aClass == null)
        {
            return false;
        }
        return ClassUtils.isSubclass(aClass, typeName);
    }

}
