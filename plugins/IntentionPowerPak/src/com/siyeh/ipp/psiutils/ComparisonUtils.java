package com.siyeh.ipp.psiutils;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ComparisonUtils{
    private static final Set s_comparisonStrings = new HashSet(6);
    private static final Map s_swappedComparisons = new HashMap(6);
    private static final Map s_invertedComparisons = new HashMap(6);

    private ComparisonUtils(){
        super();
    }

    static{
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

    public static String getFlippedComparison(String str){
        return (String) s_swappedComparisons.get(str);
    }

    public static String getNegatedComparison(String str){
        return (String) s_invertedComparisons.get(str);
    }
}
