package com.siyeh.ig.performance;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;

class ShiftUtils {
    private ShiftUtils() {
        super();
    }

    public static boolean isPowerOfTwo(PsiExpression rhs) {
        if (!(rhs instanceof PsiLiteralExpression)) {
            return false;
        }
        final PsiLiteralExpression literal = (PsiLiteralExpression) rhs;
        final Object value = literal.getValue();
        if (!(value instanceof Number)) {
            return false;
        }
        if (value instanceof Double || value instanceof Float) {
            return false;
        }
        int intValue = ((Number) value).intValue();
        if (intValue <= 1) {
            return false;
        }
        while (intValue % 2 == 0) {
            intValue >>= 1;
        }
        return intValue == 1;
    }

    public static int getLogBaseTwo(PsiExpression rhs) {
        final PsiLiteralExpression literal = (PsiLiteralExpression) rhs;
        final Object value = literal.getValue();
        int intValue = ((Number) value).intValue();
        int log = 0;
        while (intValue % 2 == 0) {
            intValue >>= 1;
            log++;
        }
        return log;
    }

}
