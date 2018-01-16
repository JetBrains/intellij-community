/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ipp.concatenation;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.*;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceFormatStringWithConcatenationIntention extends Intention {

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return element -> {
      if (!(element instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
      if (!MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_STRING, TypeUtils.getStringType(element),
                                          "format", (PsiType[])null)) {
        return false;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();

      return arguments.length > 1 && getPercentSCount(arguments[0]) == arguments.length - 1 ||
             arguments.length > 2 && getPercentSCount(arguments[1]) == arguments.length - 2;
    };
  }

  static int getPercentSCount(PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiLiteralExpression || expression instanceof PsiPolyadicExpression)
          || !ExpressionUtils.hasStringType(expression)) {
      return -1;
    }
    final Object value = ExpressionUtils.computeConstantExpression(expression);
    if (!(value instanceof String)) {
      return -1;
    }
    final String string = (String)value;
    int index = string.indexOf('%');
    final int length = string.length();
    int count = 0;
    while (index >= 0 && length > index + 1) {
      final char c = string.charAt(index + 1);
      if (c == 's') {
        count++;
      }
      else if (c != '%') {
        return -1;
      }
      index = string.indexOf('%', index + 1);
    }
    if (count == 0) {
      return -1;
    }
    return count;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression)) {
      return;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    CommentTracker commentTracker = new CommentTracker();
    final String replacementExpression =
      ExpressionUtils.hasStringType(arguments[0]) ? buildReplacementExpression(arguments, 0, commentTracker)
                                                  : buildReplacementExpression(arguments, 1, commentTracker);
    PsiReplacementUtil.replaceExpression(methodCallExpression, replacementExpression, commentTracker);
  }

  private static String buildReplacementExpression(PsiExpression[] arguments,
                                                   int indexOfFormatString,
                                                   CommentTracker commentTracker) {
    final StringBuilder builder = new StringBuilder();
    String value = (String)ExpressionUtils.computeConstantExpression(arguments[indexOfFormatString]);
    assert value != null;
    value = value.replace("%%", "%");
    int start = 0;
    int end = value.indexOf("%s");
    int count = 0;
    while (end >= 0) {
      if (end > start) {
        if (builder.length() > 0) {
          builder.append('+');
        }
        builder.append('"').append(StringUtil.escapeStringCharacters(value.substring(start, end))).append('"');
      }
      if (builder.length() > 0) {
        builder.append('+');
      }
      count++;
      final PsiExpression argument = arguments[indexOfFormatString + count];
      if (builder.length() == 0 && !ExpressionUtils.hasStringType(argument)) {
        builder.append("String.valueOf(").append(commentTracker.text(argument)).append(')');
      }
      else {
        builder.append(commentTracker.text(argument));
      }
      start = end + 2;
      end = value.indexOf("%s", start);
    }
    if (start < value.length() - 1) {
      if (builder.length() > 0) {
        builder.append('+');
      }
      builder.append('"').append(StringUtil.escapeStringCharacters(value.substring(start))).append('"');
    }
    return builder.toString();
  }
}
