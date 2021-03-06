// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.utils;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;

import java.util.HashMap;
import java.util.Map;

public final class ComparisonUtils {
  private static final Map<IElementType, String> s_comparisonStrings = new HashMap<>(6);
  private static final Map<IElementType, String> s_swappedComparisons = new HashMap<>(6);
  private static final Map<IElementType, String> s_invertedComparisons = new HashMap<>(6);

  private ComparisonUtils() {
  }

  static {
    s_comparisonStrings.put(GroovyTokenTypes.mEQUAL, "==");
    s_comparisonStrings.put(GroovyTokenTypes.mNOT_EQUAL, "!=");
    s_comparisonStrings.put(GroovyTokenTypes.mGT, ">");
    s_comparisonStrings.put(GroovyTokenTypes.mLT, "<");
    s_comparisonStrings.put(GroovyTokenTypes.mGE, ">=");
    s_comparisonStrings.put(GroovyTokenTypes.mLE, "<=");

    s_swappedComparisons.put(GroovyTokenTypes.mEQUAL, "==");
    s_swappedComparisons.put(GroovyTokenTypes.mNOT_EQUAL, "!=");
    s_swappedComparisons.put(GroovyTokenTypes.mGT, "<");
    s_swappedComparisons.put(GroovyTokenTypes.mLT, ">");
    s_swappedComparisons.put(GroovyTokenTypes.mGE, "<=");
    s_swappedComparisons.put(GroovyTokenTypes.mLE, ">=");

    s_invertedComparisons.put(GroovyTokenTypes.mEQUAL, "!=");
    s_invertedComparisons.put(GroovyTokenTypes.mNOT_EQUAL, "==");
    s_invertedComparisons.put(GroovyTokenTypes.mGT, "<=");
    s_invertedComparisons.put(GroovyTokenTypes.mLT, ">=");
    s_invertedComparisons.put(GroovyTokenTypes.mGE, "<");
    s_invertedComparisons.put(GroovyTokenTypes.mLE, ">");
  }

  public static boolean isComparison(PsiElement exp) {
    if (!(exp instanceof GrBinaryExpression)) {
      return false;
    }
    final GrBinaryExpression binaryExpression = (GrBinaryExpression) exp;
    final IElementType sign = binaryExpression.getOperationTokenType();
    return s_comparisonStrings.containsKey(sign);
  }

  public static String getStringForComparison(IElementType str) {
    return s_comparisonStrings.get(str);
  }

  public static String getFlippedComparison(IElementType str) {
    return s_swappedComparisons.get(str);
  }

  public static String getNegatedComparison(IElementType str) {
    return s_invertedComparisons.get(str);
  }
}
