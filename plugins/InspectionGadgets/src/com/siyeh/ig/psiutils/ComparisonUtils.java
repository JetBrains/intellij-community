package com.siyeh.ig.psiutils;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.tree.IElementType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ComparisonUtils {
    private ComparisonUtils() {
        super();
    }
    private static final Map s_invertedComparisons = new HashMap(6);
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

        s_invertedComparisons.put("==", "!=");
        s_invertedComparisons.put("!=", "==");
        s_invertedComparisons.put(">", "<=");
        s_invertedComparisons.put("<", ">=");
        s_invertedComparisons.put(">=", "<");
        s_invertedComparisons.put("<=", ">");

    }

    public static boolean isComparison(PsiExpression exp){
        if(!(exp instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) exp;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final String operation = sign.getText();
        return s_comparisonStrings.contains(operation);
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

    public static String getNegatedComparison(String str){
        return (String) s_invertedComparisons.get(str);
    }

}
