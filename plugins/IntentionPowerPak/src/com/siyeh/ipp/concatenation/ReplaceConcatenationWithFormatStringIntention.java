// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.concatenation;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.util.PsiConcatenationUtil;
import com.intellij.psi.util.PsiLiteralUtil;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReplaceConcatenationWithFormatStringIntention extends Intention {

  @Override
  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new Jdk5StringConcatenationPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {
    PsiPolyadicExpression expression = (PsiPolyadicExpression)element;
    PsiElement parent = expression.getParent();
    while (ExpressionUtils.isStringConcatenation(parent)) {
      expression = (PsiPolyadicExpression)parent;
      parent = expression.getParent();
    }
    final List<PsiExpression> formatParameters = new ArrayList<>();
    final String formatString = PsiConcatenationUtil.buildUnescapedFormatString(expression, true, formatParameters);
    if (replaceWithPrintfExpression(expression, formatString, formatParameters)) {
      return;
    }
    CommentTracker commentTracker = new CommentTracker();
    final StringBuilder newExpression = new StringBuilder();
    newExpression.append("java.lang.String.format(");
    appendFormatString(expression, formatString, false, newExpression);
    for (PsiExpression formatParameter : formatParameters) {
      newExpression.append(", ");
      newExpression.append(commentTracker.text(formatParameter));
    }
    newExpression.append(')');
    PsiReplacementUtil.replaceExpression(expression, newExpression.toString(), commentTracker);
  }

  private static boolean replaceWithPrintfExpression(PsiPolyadicExpression expression, String formatString,
                                                     List<PsiExpression> formatParameters) {
    final PsiElement expressionParent = expression.getParent();
    if (!(expressionParent instanceof PsiExpressionList)) {
      return false;
    }
    final PsiElement grandParent = expressionParent.getParent();
    if (!(grandParent instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final String name = methodExpression.getReferenceName();
    final boolean insertNewline;
    if ("println".equals(name)) {
      insertNewline = true;
    }
    else if ("print".equals(name)) {
      insertNewline = false;
    }
    else {
      return false;
    }
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    final String qualifiedName = containingClass.getQualifiedName();
    if (!"java.io.PrintStream".equals(qualifiedName) &&
        !"java.io.PrintWriter".equals(qualifiedName)) {
      return false;
    }
    CommentTracker commentTracker = new CommentTracker();
    final StringBuilder newExpression = new StringBuilder();
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier != null) {
      newExpression.append(commentTracker.text(qualifier)).append('.');
    }
    newExpression.append("printf(");
    appendFormatString(expression, formatString, insertNewline, newExpression);
    for (PsiExpression formatParameter : formatParameters) {
      newExpression.append(",").append(commentTracker.text(formatParameter));
    }
    newExpression.append(')');
    PsiReplacementUtil.replaceExpression(methodCallExpression, newExpression.toString(), commentTracker);
    return true;
  }

  private static void appendFormatString(PsiPolyadicExpression expression,
                                         String formatString,
                                         boolean insertNewline,
                                         StringBuilder newExpression) {
    boolean textBlocks = Arrays.stream(expression.getOperands())
      .anyMatch(operand -> operand instanceof PsiLiteralExpressionImpl &&
                           ((PsiLiteralExpressionImpl)operand).getLiteralElementType() == JavaTokenType.TEXT_BLOCK_LITERAL);
    if (textBlocks) {
      newExpression.append("\"\"\"\n");
      newExpression.append(PsiLiteralUtil.escapeTextBlockCharacters(formatString));
      if (insertNewline) {
        newExpression.append('\n');
      }
      newExpression.append("\"\"\"");
    } else {
      newExpression.append('\"');
      newExpression.append(StringUtil.escapeStringCharacters(formatString));
      if (insertNewline) {
        newExpression.append("%n");
      }
      newExpression.append('\"');
    }
  }
}
