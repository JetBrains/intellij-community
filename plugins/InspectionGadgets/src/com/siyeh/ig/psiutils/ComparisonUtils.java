package com.siyeh.ig.psiutils;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.tree.IElementType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ComparisonUtils {
    private ComparisonUtils() {
        super();
    }

    private static final Set s_comparisonStrings = new HashSet(6);
    private static final Map s_swappedComparisons = new HashMap(6);

    static {
        s_comparisonStrings.add("==");
        s_comparisonStrings.add("!=");
        s_comparisonStrings.add(">");
        s_comparisonStrings.add("<");
        s_comparisonStrings.add(">=");
        s_comparisonStrings.add("<=");

        s_swappedComparisons.put("==", "==");
        s_swappedComparisons.put("!=", "!=");
        s_swappedComparisons.put(">", "<");
        s_swappedComparisons.put("<", ">");
        s_swappedComparisons.put(">=", "<=");
        s_swappedComparisons.put("<=", ">=");

    }

    public static boolean isComparison(String str) {
        return s_comparisonStrings.contains(str);
    }

    public static String getFlippedComparison(String str) {
        return (String) s_swappedComparisons.get(str);
    }

    public static boolean isEqualityComparison(PsiBinaryExpression operator) {
        final PsiJavaToken sign = operator.getOperationSign();
        if (sign == null) {
            return false;
        }
        final IElementType tokenType = sign.getTokenType();
        return tokenType.equals(JavaTokenType.EQEQ) || tokenType.equals(JavaTokenType.NE);
    }

}
