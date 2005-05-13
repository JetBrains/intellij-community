package com.siyeh.ig.psiutils;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ComparisonUtils {
    private ComparisonUtils() {
        super();
    }
    private static final Map<String,String> s_invertedComparisons = new HashMap<String, String>(6);
    private static final Set<String> s_comparisonStrings = new HashSet<String>(6);
    private static final Map<String,String> s_swappedComparisons = new HashMap<String, String>(6);

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

        s_invertedComparisons.put("==", "!=");
        s_invertedComparisons.put("!=", "==");
        s_invertedComparisons.put(">", "<=");
        s_invertedComparisons.put("<", ">=");
        s_invertedComparisons.put(">=", "<");
        s_invertedComparisons.put("<=", ">");

    }

    public static boolean isComparison(@Nullable PsiExpression exp){
        if(!(exp instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) exp;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final String operation = sign.getText();
        return s_comparisonStrings.contains(operation);
    }

    public static boolean isComparison(@NotNull String str) {
        return s_comparisonStrings.contains(str);
    }

    public static String getFlippedComparison(@NotNull String str) {
        return s_swappedComparisons.get(str);
    }

    public static boolean isEqualityComparison(@NotNull PsiBinaryExpression operator) {
        final PsiJavaToken sign = operator.getOperationSign();
        if (sign == null) {
            return false;
        }
        final IElementType tokenType = sign.getTokenType();
        return tokenType.equals(JavaTokenType.EQEQ) || tokenType.equals(JavaTokenType.NE);
    }

    public static String getNegatedComparison(@NotNull String str){
        return s_invertedComparisons.get(str);
    }

}
