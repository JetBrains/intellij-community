// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Math conversions on PSI expressions
 */
public final class JavaPsiMathUtil {
  private JavaPsiMathUtil() {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns an expression text which evaluates to the supplied expression plus given addend. May perform some simplifications.
   * E.g. if supplied expression is "x-1" and addend is 1, then "x" will be returned.
   *
   * @param expression expression to add to
   * @param addend an addend value. Can be negative or zero.
   * @param ct comment tracker to track used comments
   * @return an expression text representing an expression with added value.
   */
  @NotNull
  public static String add(@NotNull PsiExpression expression, int addend, @NotNull CommentTracker ct) {
    if (addend == 0) return ct.text(expression);
    PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(expression);
    Number value = getNumberFromLiteral(stripped);
    if (value instanceof Integer) {
      return String.valueOf(value.intValue() + addend);
    }
    else if (value instanceof Long) {
      return String.valueOf(value.longValue() + addend);
    }
    if (stripped instanceof PsiPolyadicExpression polyadicExpression) {
      int multiplier = getMultiplier(polyadicExpression);
      if (multiplier != 0) {
        value = getNumberFromLiteral(ArrayUtil.getLastElement(polyadicExpression.getOperands()));
        String updatedAddend = null;
        if (value instanceof Integer) {
          updatedAddend = String.valueOf(value.intValue() * multiplier + addend);
        }
        else if (value instanceof Long) {
          updatedAddend = String.valueOf(value.longValue() * multiplier + addend);
        }
        if (updatedAddend != null) {
          if (updatedAddend.equals("0")) {
            updatedAddend = "";
          }
          else if (!updatedAddend.startsWith("-")) {
            updatedAddend = "+" + updatedAddend;
          }
          return updateLastAddend(polyadicExpression, updatedAddend, ct);
        }
      }
    }
    return ct.text(expression, ParenthesesUtils.ADDITIVE_PRECEDENCE) + (addend > 0 ? "+" : "") + addend;
  }

  /**
   * Tries to simplify the comparison performing algebraic conversions (e.g. "i+1 > j" -> "i >= j" or "i+3 > j+5" -> "i > j+2").
   * Note that this method does not take into account possible integer overflow, so the semantic change is possible if
   * integer overflow is considered to be a valid situation.
   *
   * @param comparison comparison to simplify
   * @param ct comment tracker to track used comments
   * @return a simplified comparison text or null if no simplification can be performed (if null is returned,
   * then comment tracker is unchanged).
   */
  @Contract("null, _ -> null")
  @Nullable
  public static String simplifyComparison(PsiExpression comparison, @NotNull CommentTracker ct) {
    if (!(comparison instanceof PsiBinaryExpression binOp)) return null;
    RelationType relationType = DfaPsiUtil.getRelationByToken(binOp.getOperationTokenType());
    if (relationType == null) return null;
    String operator = binOp.getOperationSign().getText();
    PsiExpression left = PsiUtil.skipParenthesizedExprDown(binOp.getLOperand());
    PsiExpression right = PsiUtil.skipParenthesizedExprDown(binOp.getROperand());
    if (left == null || right == null) return null;
    Integer leftValue = tryCast(getNumberFromLiteral(left), Integer.class);
    Integer rightValue = tryCast(getNumberFromLiteral(right), Integer.class);
    PsiPolyadicExpression leftPolyadic = tryCast(left, PsiPolyadicExpression.class);
    Integer leftAddend = tryCast(getLastAddend(leftPolyadic), Integer.class);
    PsiPolyadicExpression rightPolyadic = tryCast(right, PsiPolyadicExpression.class);
    Integer rightAddend = tryCast(getLastAddend(rightPolyadic), Integer.class);
    if (leftAddend != null) {
      if (leftAddend == 1 && operator.equals(">")) {
        return updateLastAddend(leftPolyadic, "", ct) + ">=" + ct.text(right);
      }
      if (leftAddend == 1 && operator.equals("<=")) {
        return updateLastAddend(leftPolyadic, "", ct) + "<" + ct.text(right);
      }
      if (leftAddend == -1 && operator.equals(">=")) {
        return updateLastAddend(leftPolyadic, "", ct) + ">" + ct.text(right);
      }
      if (leftAddend == -1 && operator.equals("<")) {
        return updateLastAddend(leftPolyadic, "", ct) + "<=" + ct.text(right);
      }
      if (rightValue != null) {
        rightValue -= leftAddend;
        return updateLastAddend(leftPolyadic, "", ct) + operator + rightValue;
      }
    }
    if (rightAddend != null) {
      if (rightAddend == 1 && operator.equals("<")) {
        return ct.text(left) + "<=" + updateLastAddend(rightPolyadic, "", ct);
      }
      if (rightAddend == 1 && operator.equals(">=")) {
        return ct.text(left) + ">" + updateLastAddend(rightPolyadic, "", ct);
      }
      if (rightAddend == -1 && operator.equals("<=")) {
        return ct.text(left) + "<" + updateLastAddend(rightPolyadic, "", ct);
      }
      if (rightAddend == -1 && operator.equals(">")) {
        return ct.text(left) + ">=" + updateLastAddend(rightPolyadic, "", ct);
      }
      if (leftValue != null) {
        leftValue -= rightAddend;
        return leftValue + operator + updateLastAddend(rightPolyadic, "", ct);
      }
    }
    if (leftAddend != null && rightAddend != null) {
      int diff = leftAddend - rightAddend;
      if (diff > 0) {
        return updateLastAddend(leftPolyadic, "+" + diff, ct) + operator + updateLastAddend(rightPolyadic, "", ct);
      } else if (diff < 0) {
        return updateLastAddend(leftPolyadic, "", ct) + operator + updateLastAddend(rightPolyadic, "+"+(-diff), ct);
      } else {
        return updateLastAddend(leftPolyadic, "", ct) + operator + updateLastAddend(rightPolyadic, "", ct);
      }
    }
    return null;
  }

  private static String updateLastAddend(PsiPolyadicExpression polyadicExpression, String updatedAddend, CommentTracker ct) {
    PsiExpression lastElement = Objects.requireNonNull(ArrayUtil.getLastElement(polyadicExpression.getOperands()));
    PsiJavaToken lastToken = Objects.requireNonNull(polyadicExpression.getTokenBeforeOperand(lastElement));
    PsiElement from = polyadicExpression.getFirstChild();
    PsiElement to = lastToken.getPrevSibling();
    return StreamEx.iterate(from, PsiElement::getNextSibling).takeWhileInclusive(e -> !to.equals(e)).map(ct::text).joining() + updatedAddend;
  }

  /**
   * Return a numeric value of literal expression or unary minus expression (like -1).
   * More complex constant expressions are deliberately not computed.
   *
   * @param expression expression to extract the numeric value from
   * @return an extracted number or null if supplied expression does not represent a number.
   */
  @Nullable
  @Contract("null->null")
  public static Number getNumberFromLiteral(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiLiteralExpression) {
      return tryCast(((PsiLiteralExpression)expression).getValue(), Number.class);
    }
    if (expression instanceof PsiUnaryExpression && ((PsiUnaryExpression)expression).getOperationTokenType().equals(JavaTokenType.MINUS)) {
      PsiExpression operand = ((PsiUnaryExpression)expression).getOperand();
      if (operand instanceof PsiLiteralExpression) {
        Object value = ((PsiLiteralExpression)operand).getValue();
        return negate(value);
      }
    }
    return null;
  }

  /**
   * Negate given number of type Integer, Long, Double or Float, preserving the original type
   * @param value number to negate
   * @return negated number; null if supplied value is not supported.
   */
  @Nullable
  public static Number negate(Object value) {
    if (value instanceof Integer) {
      return -((Integer)value).intValue();
    }
    if (value instanceof Long) {
      return -((Long)value).longValue();
    }
    if (value instanceof Double) {
      return -((Double)value).doubleValue();
    }
    if (value instanceof Float) {
      return -((Float)value).floatValue();
    }
    return null;
  }

  private static int getMultiplier(PsiPolyadicExpression polyadicExpression) {
    int multiplier;
    IElementType type = polyadicExpression.getOperationTokenType();
    if (type.equals(JavaTokenType.PLUS)) {
      multiplier = 1;
    }
    else if (type.equals(JavaTokenType.MINUS)) {
      multiplier = -1;
    }
    else {
      multiplier = 0;
    }
    return multiplier;
  }

  @Nullable
  private static Number getLastAddend(PsiPolyadicExpression polyadic) {
    if (polyadic != null) {
      int multiplier = getMultiplier(polyadic);
      if (multiplier != 0) {
        PsiExpression lastOp = ArrayUtil.getLastElement(polyadic.getOperands());
        Number lastAddend = getNumberFromLiteral(lastOp);
        if (multiplier == 1) {
          return lastAddend;
        }
        else if (multiplier == -1) {
          return negate(lastAddend);
        }
      }
    }
    return null;
  }

  /**
   * Returns long range set for expression compared to a constant  or null if comparison is unsupported.
   * Supports int and long constants. Supports nonConstantOperand of any type.
   * E.g. if comparison expression is nonConstantOperand < 1, the {Long.MIN_VALUE..0} will be returned.
   * Note: returned LongRangeSet may contain numbers outside of int range, even if nonConstantOperand has int type
   *
   * @param nonConstantOperand expression which is compared to int or long constant.
   * @return long range set
   */
  @Nullable
  public static LongRangeSet getRangeFromComparison(@NotNull PsiExpression nonConstantOperand) {
    final PsiBinaryExpression binOp =
      tryCast(PsiUtil.skipParenthesizedExprUp(nonConstantOperand.getParent()), PsiBinaryExpression.class);
    if (binOp == null) return null;
    final PsiJavaToken sign = binOp.getOperationSign();
    final IElementType tokenType = sign.getTokenType();
    RelationType relation = DfaPsiUtil.getRelationByToken(tokenType);
    if (relation == null) return null;
    final PsiExpression constOperand =
      PsiTreeUtil.isAncestor(binOp.getLOperand(), nonConstantOperand, false) ? binOp.getROperand() : binOp.getLOperand();
    if (constOperand == null) return null;
    final Object constantExpression = ExpressionUtils.computeConstantExpression(constOperand);
    if (!(constantExpression instanceof Integer) && !(constantExpression instanceof Long)) return null;
    long value = ((Number)constantExpression).longValue();
    boolean yodaCondition = constOperand == binOp.getLOperand();
    if (yodaCondition) {
      relation = relation.getFlipped();
    }
    return LongRangeSet.point(value).fromRelation(relation);
  }
}
