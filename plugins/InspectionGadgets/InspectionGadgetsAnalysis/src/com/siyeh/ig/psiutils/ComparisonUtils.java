/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ComparisonUtils {

  private ComparisonUtils() {}

  private static final Set<IElementType> s_comparisonTokens = new HashSet<>(6);

  private static final Map<IElementType, String> s_swappedComparisons = new HashMap<>(6);

  private static final Map<IElementType, String> s_invertedComparisons = new HashMap<>(6);

  static {
    s_comparisonTokens.add(JavaTokenType.EQEQ);
    s_comparisonTokens.add(JavaTokenType.NE);
    s_comparisonTokens.add(JavaTokenType.GT);
    s_comparisonTokens.add(JavaTokenType.LT);
    s_comparisonTokens.add(JavaTokenType.GE);
    s_comparisonTokens.add(JavaTokenType.LE);

    s_swappedComparisons.put(JavaTokenType.EQEQ, "==");
    s_swappedComparisons.put(JavaTokenType.NE, "!=");
    s_swappedComparisons.put(JavaTokenType.GT, "<");
    s_swappedComparisons.put(JavaTokenType.LT, ">");
    s_swappedComparisons.put(JavaTokenType.GE, "<=");
    s_swappedComparisons.put(JavaTokenType.LE, ">=");

    s_invertedComparisons.put(JavaTokenType.EQEQ, "!=");
    s_invertedComparisons.put(JavaTokenType.NE, "==");
    s_invertedComparisons.put(JavaTokenType.GT, "<=");
    s_invertedComparisons.put(JavaTokenType.LT, ">=");
    s_invertedComparisons.put(JavaTokenType.GE, "<");
    s_invertedComparisons.put(JavaTokenType.LE, ">");
  }

  /**
   * Returns the actual type of compared values in comparison expression after unboxing and promotion if applicable.
   *
   * @param expression the expression to get the type of compared values
   * @return the resulting type or null if expression is not a comparison or type is not known.
   */
  @Contract("null -> null")
  @Nullable
  public static PsiType getComparisonType(PsiExpression expression) {
    if(!(expression instanceof PsiPolyadicExpression)) return null;
    PsiPolyadicExpression operation = (PsiPolyadicExpression)expression;
    IElementType tokenType = operation.getOperationTokenType();
    if (!isComparisonOperation(tokenType)) return null;
    PsiType lType;
    PsiType rType;
    if(operation instanceof PsiBinaryExpression) {
      PsiExpression left = ((PsiBinaryExpression)operation).getLOperand();
      PsiExpression right = ((PsiBinaryExpression)operation).getROperand();
      lType = left.getType();
      rType = right == null ? null : right.getType();
    } else {
      PsiExpression[] operands = operation.getOperands();
      if(operands.length <= 2) return null;
      lType = PsiType.BOOLEAN;
      rType = operands[operands.length-1].getType();
    }
    if (lType == null || rType == null) return null;
    if (lType.equals(rType)) return lType;
    return TypeConversionUtil.unboxAndBalanceTypes(lType, rType);
  }

  public static boolean isComparison(@Nullable PsiExpression expression) {
    if (!(expression instanceof PsiPolyadicExpression)) {
      return false;
    }
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
    final IElementType tokenType = polyadicExpression.getOperationTokenType();
    return isComparisonOperation(tokenType);
  }

  public static boolean isComparisonOperation(IElementType tokenType) {
    return s_comparisonTokens.contains(tokenType);
  }

  public static String getFlippedComparison(IElementType tokenType) {
    return s_swappedComparisons.get(tokenType);
  }

  public static boolean isEqualityComparison(@NotNull PsiExpression expression) {
    if (!(expression instanceof PsiPolyadicExpression)) {
      return false;
    }
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
    final IElementType tokenType = polyadicExpression.getOperationTokenType();
    return tokenType.equals(JavaTokenType.EQEQ) || tokenType.equals(JavaTokenType.NE);
  }

  public static String getNegatedComparison(IElementType tokenType) {
    return s_invertedComparisons.get(tokenType);
  }
}
