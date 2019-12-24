/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static com.intellij.codeInspection.util.OptionalUtil.*;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_OPTIONAL;

public class BoolUtils {

  private BoolUtils() {}

  public static boolean isNegation(@NotNull PsiExpression expression) {
    if (!(expression instanceof PsiPrefixExpression)) {
      return false;
    }
    final PsiPrefixExpression prefixExp = (PsiPrefixExpression)expression;
    final IElementType tokenType = prefixExp.getOperationTokenType();
    return JavaTokenType.EXCL.equals(tokenType);
  }

  public static boolean isNegated(PsiExpression exp) {
    PsiExpression ancestor = exp;
    PsiElement parent = ancestor.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      ancestor = (PsiExpression)parent;
      parent = ancestor.getParent();
    }
    return parent instanceof PsiExpression && isNegation((PsiExpression)parent);
  }

  @Nullable
  public static PsiExpression getNegated(PsiExpression expression) {
    if (!(expression instanceof PsiPrefixExpression)) {
      return null;
    }
    final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
    final IElementType tokenType = prefixExpression.getOperationTokenType();
    if (!JavaTokenType.EXCL.equals(tokenType)) {
      return null;
    }
    final PsiExpression operand = prefixExpression.getOperand();
    PsiExpression stripped = ParenthesesUtils.stripParentheses(operand);
    return stripped == null ? operand : stripped;
  }

  @NotNull
  public static String getNegatedExpressionText(@Nullable PsiExpression condition) {
    return getNegatedExpressionText(condition, new CommentTracker());
  }

  @NotNull
  public static String getNegatedExpressionText(@Nullable PsiExpression condition, CommentTracker tracker) {
    return getNegatedExpressionText(condition, ParenthesesUtils.NUM_PRECEDENCES, tracker);
  }

  private static final CallMatcher STREAM_ANY_MATCH = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "anyMatch");
  private static final CallMatcher STREAM_NONE_MATCH = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "noneMatch");

  private static final CallMatcher OPTIONAL_IS_PRESENT =
    CallMatcher.anyOf(
      CallMatcher.exactInstanceCall(JAVA_UTIL_OPTIONAL, "isPresent").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_INT, "isPresent").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_LONG, "isPresent").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_DOUBLE, "isPresent").parameterCount(0)
    );
  private static final CallMatcher OPTIONAL_IS_EMPTY =
    CallMatcher.anyOf(
      CallMatcher.exactInstanceCall(JAVA_UTIL_OPTIONAL, "isEmpty").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_INT, "isEmpty").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_LONG, "isEmpty").parameterCount(0),
      CallMatcher.exactInstanceCall(OPTIONAL_DOUBLE, "isEmpty").parameterCount(0)
    );

  private static class PredicatedReplacement {
    Predicate<PsiMethodCallExpression> predicate;
    String name;

    private PredicatedReplacement(Predicate<PsiMethodCallExpression> predicate, String name) {
      this.predicate = predicate;
      this.name = name;
    }
  }

  private static final List<PredicatedReplacement> ourReplacements = new ArrayList<>();
  static {
    ourReplacements.add(new PredicatedReplacement(OPTIONAL_IS_EMPTY, "isPresent"));
    ourReplacements.add(new PredicatedReplacement(OPTIONAL_IS_PRESENT.withLanguageLevelAtLeast(LanguageLevel.JDK_11), "isEmpty"));
    ourReplacements.add(new PredicatedReplacement(STREAM_ANY_MATCH, "noneMatch"));
    ourReplacements.add(new PredicatedReplacement(STREAM_NONE_MATCH, "anyMatch"));
  }

  private static String findSmartMethodNegation(PsiExpression expression) {
    if (!(expression instanceof PsiMethodCallExpression)) return null;
    PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
    PsiMethodCallExpression copy = (PsiMethodCallExpression)call.copy();
    for (PredicatedReplacement predicatedReplacement : ourReplacements) {
      if (predicatedReplacement.predicate.test(call)) {
        ExpressionUtils.bindCallTo(copy, predicatedReplacement.name);
        return copy.getText();
      }
    }
    return null;
  }

  @NotNull
  public static String getNegatedExpressionText(@Nullable PsiExpression expression,
                                                int precedence,
                                                CommentTracker tracker) {
    if (expression == null) {
      return "";
    }
    if (expression instanceof PsiMethodCallExpression) {
      String smartNegation = findSmartMethodNegation(expression);
      if (smartNegation != null) return smartNegation;
    }
    if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      PsiExpression operand = parenthesizedExpression.getExpression();
      if (operand != null) {
        return '(' + getNegatedExpressionText(operand, tracker) + ')';
      }
    }
    if (expression instanceof PsiAssignmentExpression && expression.getParent() instanceof PsiExpressionStatement) {
      String newOp = null;
      IElementType tokenType = ((PsiAssignmentExpression)expression).getOperationTokenType();
      if (tokenType == JavaTokenType.ANDEQ) {
        newOp = "|=";
      }
      else if (tokenType == JavaTokenType.OREQ) {
        newOp = "&=";
      }
      if (newOp != null) {
        return tracker.text(((PsiAssignmentExpression)expression).getLExpression()) + 
               newOp +
               getNegatedExpressionText(((PsiAssignmentExpression)expression).getRExpression());
      }
    }
    if (expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)expression;
      final boolean needParenthesis = ParenthesesUtils.getPrecedence(conditionalExpression) >= precedence;
      final String text = tracker.text(conditionalExpression.getCondition()) +
                          '?' + getNegatedExpressionText(conditionalExpression.getThenExpression(), tracker) +
                          ':' + getNegatedExpressionText(conditionalExpression.getElseExpression(), tracker);
      return needParenthesis ? "(" + text + ")" : text;
    }
    if (isNegation(expression)) {
      final PsiExpression negated = getNegated(expression);
      if (negated != null) {
        return ParenthesesUtils.getText(tracker.markUnchanged(negated), precedence);
      }
    }
    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      final PsiExpression[] operands = polyadicExpression.getOperands();
      if (ComparisonUtils.isComparison(polyadicExpression)) {
        final String negatedComparison = ComparisonUtils.getNegatedComparison(tokenType);
        final StringBuilder result = new StringBuilder();
        final boolean isEven = (operands.length & 1) != 1;
        for (int i = 0, length = operands.length; i < length; i++) {
          final PsiExpression operand = operands[i];
          if (TypeUtils.hasFloatingPointType(operand) && !ComparisonUtils.isEqualityComparison(polyadicExpression)) {
            // preserve semantics for NaNs
            return "!(" + polyadicExpression.getText() + ')';
          }
          if (i > 0) {
            if (isEven && (i & 1) != 1) {
              final PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(operand);
              if (token != null) {
                result.append(token.getText());
              }
            }
            else {
              result.append(negatedComparison);
            }
          }
          result.append(tracker.text(operand));
        }
        return result.toString();
      }
      if(tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
        final String targetToken;
        final int newPrecedence;
        if (tokenType.equals(JavaTokenType.ANDAND)) {
          targetToken = "||";
          newPrecedence = ParenthesesUtils.OR_PRECEDENCE;
        }
        else {
          targetToken = "&&";
          newPrecedence = ParenthesesUtils.AND_PRECEDENCE;
        }
        final Function<PsiElement, String> replacer = child -> {
          if (child instanceof PsiExpression) {
            return getNegatedExpressionText((PsiExpression)child, newPrecedence, tracker);
          }
          return child instanceof PsiJavaToken ? targetToken : tracker.text(child);
        };
        final String join = StringUtil.join(polyadicExpression.getChildren(), replacer, "");
        return (newPrecedence > precedence) ? '(' + join + ')' : join;
      }
    }
    if (expression instanceof PsiLiteralExpression) {
      Object value = ((PsiLiteralExpression)expression).getValue();
      if (value instanceof Boolean) {
        return String.valueOf(!((Boolean)value));
      }
    }
    return '!' + ParenthesesUtils.getText(tracker.markUnchanged(expression), ParenthesesUtils.PREFIX_PRECEDENCE);
  }

  @Nullable
  public static PsiExpression findNegation(PsiExpression expression) {
    PsiExpression ancestor = expression;
    PsiElement parent = ancestor.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      ancestor = (PsiExpression)parent;
      parent = ancestor.getParent();
    }
    if (parent instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixAncestor = (PsiPrefixExpression)parent;
      if (JavaTokenType.EXCL.equals(prefixAncestor.getOperationTokenType())) {
        return prefixAncestor;
      }
    }
    return null;
  }

  @Contract("null -> false")
  public static boolean isBooleanLiteral(PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiLiteralExpression)) {
      return false;
    }
    final PsiLiteralExpression literalExpression = (PsiLiteralExpression)expression;
    @NonNls final String text = literalExpression.getText();
    return PsiKeyword.TRUE.equals(text) || PsiKeyword.FALSE.equals(text);
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isTrue(@Nullable PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (expression == null) {
      return false;
    }
    return PsiKeyword.TRUE.equals(expression.getText());
  }

  @Contract(value ="null -> false", pure = true)
  public static boolean isFalse(@Nullable PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (expression == null) {
      return false;
    }
    return PsiKeyword.FALSE.equals(expression.getText());
  }

  /**
   * Checks whether two supplied boolean expressions are opposite to each other (e.g. "a == null" and "a != null")
   *
   * @param expression1 first expression
   * @param expression2 second expression
   * @return true if it's determined that the expressions are opposite to each other.
   */
  @Contract(value = "null, _ -> false; _, null -> false", pure = true)
  public static boolean areExpressionsOpposite(@Nullable PsiExpression expression1, @Nullable PsiExpression expression2) {
    expression1 = PsiUtil.skipParenthesizedExprDown(expression1);
    expression2 = PsiUtil.skipParenthesizedExprDown(expression2);
    if (expression1 == null || expression2 == null) return false;
    EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
    if (isNegation(expression1)) {
      return equivalence.expressionsAreEquivalent(getNegated(expression1), expression2);
    }
    if (isNegation(expression2)) {
      return equivalence.expressionsAreEquivalent(getNegated(expression2), expression1);
    }
    if (expression1 instanceof PsiBinaryExpression && expression2 instanceof PsiBinaryExpression) {
      PsiBinaryExpression binOp1 = (PsiBinaryExpression)expression1;
      PsiBinaryExpression binOp2 = (PsiBinaryExpression)expression2;
      RelationType rel1 = RelationType.fromElementType(binOp1.getOperationTokenType());
      RelationType rel2 = RelationType.fromElementType(binOp2.getOperationTokenType());
      if (rel1 == null || rel2 == null) return false;
      PsiType type = binOp1.getLOperand().getType();
      // a > b and a <= b are not strictly opposite due to NaN semantics
      if (type == null || type.equals(PsiType.FLOAT) || type.equals(PsiType.DOUBLE)) return false;
      if (rel1 == rel2.getNegated()) {
        return equivalence.expressionsAreEquivalent(binOp1.getLOperand(), binOp2.getLOperand()) &&
               equivalence.expressionsAreEquivalent(binOp1.getROperand(), binOp2.getROperand());
      }
      if (rel1.getFlipped() == rel2.getNegated()) {
        return equivalence.expressionsAreEquivalent(binOp1.getLOperand(), binOp2.getROperand()) &&
               equivalence.expressionsAreEquivalent(binOp1.getROperand(), binOp2.getLOperand());
      }
    }
    return false;
  }
}