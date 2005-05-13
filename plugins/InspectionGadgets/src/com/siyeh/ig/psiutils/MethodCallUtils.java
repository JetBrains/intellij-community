package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

public class MethodCallUtils {
    private MethodCallUtils() {
        super();

    }

    public static String getMethodName(@NotNull PsiMethodCallExpression expression) {
        final PsiReferenceExpression method = expression.getMethodExpression();
        if (method == null) {
            return null;
        }
        return method.getReferenceName();
    }

    public static PsiType getTargetType(@NotNull PsiMethodCallExpression expression) {
        final PsiReferenceExpression method = expression.getMethodExpression();
        if (method == null) {
            return null;
        }
        final PsiExpression qualifierExpression = method.getQualifierExpression();
        if (qualifierExpression == null) {
            return null;
        }
        return qualifierExpression.getType();
    }
}
