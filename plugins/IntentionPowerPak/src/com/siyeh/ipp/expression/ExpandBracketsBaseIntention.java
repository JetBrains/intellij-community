// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.expression;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public abstract class ExpandBracketsBaseIntention extends Intention {

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    processIntention(null, element);
  }

  @Override
  protected void processIntention(Editor editor, @NotNull PsiElement element) {
    final List<PsiExpression> possibleInnerExpressions = getPossibleInnerExpressions(element, getSupportedOperations(), getPrefixes());
    processInnerExpression(editor, possibleInnerExpressions);
  }

  private void processInnerExpression(@Nullable Editor editor, @NotNull List<PsiExpression> expressions) {
    final Pass<PsiExpression> callback = new Pass<PsiExpression>() {
      @Override
      public void pass(PsiExpression expression) {
        WriteCommandAction.writeCommandAction(expression.getProject(), expression.getContainingFile())
          .run(() -> replaceExpression(expression));
      }
    };

    if (expressions.size() == 1) {
      callback.pass(expressions.get(0));
      return;
    }

    if (expressions.isEmpty() || editor == null) return;

    final PsiExpressionTrimRenderer.RenderFunction renderer = new PsiExpressionTrimRenderer.RenderFunction();
    IntroduceTargetChooser.showChooser(editor, expressions, callback, renderer);
  }

  private void replaceExpression(@NotNull PsiExpression innerExpr) {
    while (innerExpr.getParent() instanceof PsiPrefixExpression) innerExpr = (PsiExpression)innerExpr.getParent();

    PsiExpression operand = getPolyadicParent(innerExpr, getConjunctionTokens());
    if (operand == null) operand = innerExpr;

    final PsiPolyadicExpression parent = getPolyadicParent(operand, getDisjunctionTokens());

    final String replacement = expandBrackets(parent, operand, innerExpr);
    if (replacement == null) return;

    final CommentTracker commentTracker = new CommentTracker();

    if (parent == null) {
      PsiReplacementUtil.replaceExpression(operand, replacement, commentTracker);
    }
    else {
      PsiReplacementUtil.replaceExpression(parent, replacement, commentTracker);
    }
  }

  @Nullable
  private String expandBrackets(@Nullable PsiPolyadicExpression outerExpr,
                                @NotNull PsiExpression bracketsOperand,
                                @NotNull PsiExpression innerExpr) {
    final StringBuilder sb = new StringBuilder();

    if (outerExpr == null) {
      if (!addReplacement(null, bracketsOperand, innerExpr, sb)) return null;
      return sb.toString();
    }

    for (PsiExpression operand : outerExpr.getOperands()) {
      final PsiJavaToken token = outerExpr.getTokenBeforeOperand(operand);

      if (operand != bracketsOperand) {
        if (token != null) sb.append(token.getText());
        sb.append(operand.getText());
        continue;
      }

      if (!addReplacement(token, bracketsOperand, innerExpr, sb)) return null;
    }

    return sb.toString();
  }

  /**
   * Transforms operand by expanding brackets for innerExpr
   * and appends transformed operand to sb (e.g. {@code} <b>a * (b + c) -> a * b + a * c</b>).
   * <p>Note that operand can be equal to innerExpr in case if innerExpr is inside disjunction expression (e.g. <b>a + (b + c)</b>).
   *
   * @param token     token before operand. possibly null if operand is first in expression or not in expression at all
   * @param operand   operand with innerExpr. might be equal to innerExpr
   * @param innerExpr parenthesised expression with prefixes if any
   * @param sb        string builder to append result to
   * @return true if appended, false otherwise
   */
  protected abstract boolean addReplacement(@Nullable PsiJavaToken token, @NotNull PsiExpression operand,
                                            @NotNull PsiExpression innerExpr, @NotNull StringBuilder sb);

  @NotNull
  protected abstract IElementType[] getConjunctionTokens();

  @NotNull
  protected abstract IElementType[] getDisjunctionTokens();

  @NotNull
  protected abstract IElementType[] getPrefixes();

  @NotNull
  private IElementType[] getSupportedOperations() {
    return ArrayUtil.mergeArrays(getConjunctionTokens(), getDisjunctionTokens());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        return !getPossibleInnerExpressions(element, getSupportedOperations(), getPrefixes()).isEmpty();
      }
    };
  }

  @NotNull
  private List<PsiExpression> getPossibleInnerExpressions(@NotNull PsiElement element,
                                                          @NotNull IElementType[] supportedOperations,
                                                          @NotNull IElementType[] prefixes) {
    final List<PsiExpression> possibleExpressions = new ArrayList<>();

    if (!(element instanceof PsiJavaToken)) return possibleExpressions;

    while ((element = PsiTreeUtil.getParentOfType(element, PsiParenthesizedExpression.class)) != null) {
      final PsiParenthesizedExpression expression = (PsiParenthesizedExpression)element;

      final PsiPolyadicExpression innerExpr =
        ObjectUtils.tryCast(ParenthesesUtils.stripParentheses(expression.getExpression()), PsiPolyadicExpression.class);
      if (!isSupportedExpression(innerExpr, supportedOperations)) continue;

      final PsiExpression outerExpr = ObjectUtils.tryCast(expression.getParent(), PsiExpression.class);
      if (!isSupportedOuterExpression(outerExpr, expression, innerExpr, supportedOperations, prefixes)) continue;

      possibleExpressions.add(expression);
    }

    return possibleExpressions;
  }

  protected boolean isSupportedOuterExpression(@Nullable PsiExpression outerExpr,
                                               @NotNull PsiParenthesizedExpression expression,
                                               @NotNull PsiPolyadicExpression innerExpr,
                                               @NotNull IElementType[] supportedOperations,
                                               @NotNull IElementType[] prefixes) {
    PsiPrefixExpression prefixExpression = ObjectUtils.tryCast(outerExpr, PsiPrefixExpression.class);
    if (prefixExpression != null) {
      do {
        if (!ArrayUtil.contains(prefixExpression.getOperationTokenType(), prefixes)) return false;
        prefixExpression = ObjectUtils.tryCast(prefixExpression.getParent(), PsiPrefixExpression.class);
      }
      while (prefixExpression != null);
      return true;
    }

    final PsiPolyadicExpression polyadicExpression = ObjectUtils.tryCast(outerExpr, PsiPolyadicExpression.class);
    if (polyadicExpression != null) {
      if (!isSupportedExpression(polyadicExpression, supportedOperations)) return false;

      final int outerExprPrecedence = PsiPrecedenceUtil.getPrecedence(polyadicExpression);
      final int innerExprPrecedence = PsiPrecedenceUtil.getPrecedence(innerExpr);

      if (outerExprPrecedence < innerExprPrecedence) return true;
    }

    return false;
  }

  @Nullable
  private static PsiPolyadicExpression getPolyadicParent(@NotNull PsiElement element, @NotNull IElementType... types) {
    final PsiPolyadicExpression parent = ObjectUtils.tryCast(element.getParent(), PsiPolyadicExpression.class);
    return parent != null && ArrayUtil.contains(parent.getOperationTokenType(), types) ? parent : null;
  }

  @NotNull
  protected static String getConjunctionFormat(@NotNull PsiPolyadicExpression expression, @NotNull PsiExpression toReplace) {
    final StringBuilder sb = new StringBuilder();

    for (PsiExpression operand : expression.getOperands()) {
      final PsiJavaToken token = expression.getTokenBeforeOperand(operand);
      if (token != null) sb.append(token.getText());

      if (operand != toReplace) {
        sb.append(operand.getText());
        continue;
      }

      sb.append("%s");
    }

    return sb.toString();
  }

  @NotNull
  protected static <T> T processPrefixed(@NotNull PsiExpression expression,
                                         @NotNull BiFunction<? super PsiExpression, ? super Boolean, T> func,
                                         @NotNull IElementType negationTokenType, @NotNull IElementType... skipTokenTypes) {
    boolean isInverted = false;
    while (expression instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpr = (PsiPrefixExpression)expression;
      final IElementType tokenType = prefixExpr.getOperationTokenType();
      if (!tokenType.equals(negationTokenType) && !ArrayUtil.contains(tokenType, skipTokenTypes)) break;

      if (tokenType.equals(negationTokenType)) isInverted = !isInverted;

      expression = prefixExpr.getOperand();
    }

    return func.apply(expression, isInverted);
  }

  private static boolean isSupportedExpression(PsiPolyadicExpression expression, @NotNull IElementType[] supportedOperations) {
    return expression != null && expression.getOperands().length >= 2 &&
           ArrayUtil.contains(expression.getOperationTokenType(), supportedOperations);
  }
}
