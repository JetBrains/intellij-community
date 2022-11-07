// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.redundancy;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryStringEscapeInspection extends BaseInspection implements CleanupLocalInspectionTool {

  public boolean reportChars = false;

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.string.escape.problem.descriptor");
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionGadgetsBundle.message("inspection.unnecessary.string.escape.report.char.literals.option"), this, "reportChars");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final String expressionText = (String)infos[0];
    return new UnnecessaryStringEscapeFix(expressionText);
  }

  private static class UnnecessaryStringEscapeFix extends InspectionGadgetsFix {

    private final String myText;

    UnnecessaryStringEscapeFix(String text) {
      myText = text;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.string.escape.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiLiteralExpression)) {
        return;
      }
      final PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
      final PsiType type = literalExpression.getType();
      if (type == null) {
        return;
      }
      final String text = literalExpression.getText();
      if (!myText.equals(text)) {
        return;
      }
      if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        final StringBuilder newExpression = new StringBuilder();
        if (literalExpression.isTextBlock()) {
          int offset = 0;
          int start = findUnnecessaryTextBlockEscapes(text, 4);
          while (start >= 0) {
            newExpression.append(text, offset, start);
            offset = start + 2;
            @NonNls final String escape = text.substring(start, offset);
            if ("\\n".equals(escape)) {
              final int indent = PsiLiteralUtil.getTextBlockIndent(literalExpression);
              if (indent < 0) return;
              newExpression.append('\n').append(StringUtil.repeatSymbol(' ', indent));
            }
            else {
              newExpression.append(escape.charAt(1));
            }
            start = findUnnecessaryTextBlockEscapes(text, offset);
          }
          newExpression.append(text.substring(offset));
          final Document document = element.getContainingFile().getViewProvider().getDocument();
          assert document != null;
          final TextRange replaceRange = element.getTextRange();
          document.replaceString(replaceRange.getStartOffset(), replaceRange.getEndOffset(), newExpression.toString());
          return;
        }
        else {
          boolean escaped = false;
          final int length = text.length();
          for (int i = 0; i < length; i++) {
            final char c = text.charAt(i);
            if (escaped) {
              if (c != '\'') newExpression.append('\\');
              newExpression.append(c);
              escaped = false;
            }
            else if (c == '\\') escaped = true;
            else newExpression.append(c);
          }
        }
        PsiReplacementUtil.replaceExpression(literalExpression, newExpression.toString());
      }
      else if (PsiType.CHAR.equals(type) && text.equals("'\\\"'")) {
        PsiReplacementUtil.replaceExpression(literalExpression, "'\"'");
      }
    }
  }

  static int findUnnecessaryTextBlockEscapes(String text, int start) {
    boolean slash = false;
    boolean ws = false;
    int doubleQuotes = 0;
    final int max = text.length() - 3; // skip closing """
    for (int i = start; i < max; i++) {
      final char ch = text.charAt(i);
      if (ch == '\\') slash = !slash;
      else if (ch == ' ' || ch == '\t') ws = true;
      else {
        if (slash) {
          if (ch == 'n') {
            if (!ws) return i - 1;
          }
          else if (ch == '\'') {
            return i - 1;
          }
          else if (ch == '"' && doubleQuotes < 2) {
            if (i == max - 1) return -1;
            if (i == max - 2) return i - 1;
            if (i < max - 2 && text.charAt(i + 1) == '"') {
              if (doubleQuotes != 1 && text.charAt(i + 2) != '"') return i - 1;
            }
            else {
              return i - 1;
            }
          }
          doubleQuotes = 0;
        }
        else if (ch == '"') doubleQuotes++;
        else doubleQuotes = 0;
        slash = false;
        ws = false;
      }
    }
    return -1;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RedundantStringEscapeVisitor();
  }

  private class RedundantStringEscapeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      HighlightInfo.Builder
        parsingError = HighlightUtil.checkLiteralExpressionParsingError(expression, PsiUtil.getLanguageLevel(expression), null, null);
      if (parsingError != null) {
        return;
      }
      if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        if (expression.isTextBlock()) {
          final String text = expression.getText();
          int start = findUnnecessaryTextBlockEscapes(text, 4);
          while (start >= 0) {
            registerErrorAtOffset(expression, start, 2, text);
            start = findUnnecessaryTextBlockEscapes(text, start + 2);
          }
        }
        else {
          final String text = expression.getText();
          boolean slash = false;
          final int max = text.length() - 1; // skip closing "
          for (int i = 1; i < max; i++) {
            final char c = text.charAt(i);
            if (slash) {
              slash = false;
              if (c == '\'') registerErrorAtOffset(expression, i - 1, 2, text);
            }
            else if (c == '\\') slash = true;
          }
        }
      }
      else if (reportChars && PsiType.CHAR.equals(type)) {
        final String text = expression.getText();
        if ("'\\\"'".equals(text)) {
          registerErrorAtOffset(expression, 1, 2, text);
        }
      }
    }
  }
}
