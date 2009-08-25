/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.psiutils;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ComparisonUtils{

    private static final Set<String> comparisons =
            new HashSet<String>(6);
    private static final Map<String, String> flippedComparisons =
            new HashMap<String, String>(6);
    private static final Map<String, String> negatedComparisons =
            new HashMap<String, String>(6);

    private ComparisonUtils(){
    }

    static {
        comparisons.add("==");
        comparisons.add("!=");
        comparisons.add(">");
        comparisons.add("<");
        comparisons.add(">=");
        comparisons.add("<=");

        flippedComparisons.put("==", "==");
        flippedComparisons.put("!=", "!=");
        flippedComparisons.put(">", "<");
        flippedComparisons.put("<", ">");
        flippedComparisons.put(">=", "<=");
        flippedComparisons.put("<=", ">=");

        negatedComparisons.put("==", "!=");
        negatedComparisons.put("!=", "==");
        negatedComparisons.put(">", "<=");
        negatedComparisons.put("<", ">=");
        negatedComparisons.put(">=", "<");
        negatedComparisons.put("<=", ">");
    }

    public static boolean isComparison(@Nullable PsiExpression expression){
        if(!(expression instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        return isComparison(sign);
    }

    public static boolean isComparison(@NotNull PsiJavaToken sign) {
        final String text = sign.getText();
        return comparisons.contains(text);
    }

    public static String getFlippedComparison(@NotNull PsiJavaToken sign){
        final String text = sign.getText();
        return flippedComparisons.get(text);
    }

    public static String getNegatedComparison(@NotNull PsiJavaToken sign){
        final String text = sign.getText();
        return negatedComparisons.get(text);
    }
}