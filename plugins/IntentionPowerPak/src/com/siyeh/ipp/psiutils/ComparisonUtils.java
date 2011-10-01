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

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ComparisonUtils {

  private static final Set<IElementType> comparisons =
    new HashSet<IElementType>(6);
  private static final Map<IElementType, String> flippedComparisons =
    new HashMap<IElementType, String>(6);
  private static final Map<IElementType, String> negatedComparisons =
    new HashMap<IElementType, String>(6);

  private ComparisonUtils() {
  }

  static {
    comparisons.add(JavaTokenType.EQEQ);
    comparisons.add(JavaTokenType.NE);
    comparisons.add(JavaTokenType.GT);
    comparisons.add(JavaTokenType.LT);
    comparisons.add(JavaTokenType.GE);
    comparisons.add(JavaTokenType.LE);

    flippedComparisons.put(JavaTokenType.EQEQ, "==");
    flippedComparisons.put(JavaTokenType.NE, "!=");
    flippedComparisons.put(JavaTokenType.GT, "<");
    flippedComparisons.put(JavaTokenType.LT, ">");
    flippedComparisons.put(JavaTokenType.GE, "<=");
    flippedComparisons.put(JavaTokenType.LE, ">=");

    negatedComparisons.put(JavaTokenType.EQEQ, "!=");
    negatedComparisons.put(JavaTokenType.NE, "==");
    negatedComparisons.put(JavaTokenType.GT, "<=");
    negatedComparisons.put(JavaTokenType.LT, ">=");
    negatedComparisons.put(JavaTokenType.GE, "<");
    negatedComparisons.put(JavaTokenType.LE, ">");
  }

  public static boolean isComparison(@Nullable PsiExpression expression) {
    if (!(expression instanceof PsiBinaryExpression)) {
      return false;
    }
    final PsiBinaryExpression binaryExpression =
      (PsiBinaryExpression)expression;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    return isComparison(tokenType);
  }

  public static boolean isComparison(@NotNull IElementType tokenType) {
    return comparisons.contains(tokenType);
  }

  public static String getFlippedComparison(@NotNull PsiJavaToken sign) {
    final IElementType text = sign.getTokenType();
    return getFlippedComparison(text);
  }

  public static String getFlippedComparison(IElementType text) {
    return flippedComparisons.get(text);
  }

  public static String getNegatedComparison(IElementType tokenType) {
    return negatedComparisons.get(tokenType);
  }
}