// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryStringEscapeInspection extends BaseInspection implements CleanupLocalInspectionTool {

  public boolean reportChars = false;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessary.string.escape.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.string.escape.problem.descriptor");
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Report char literals", this, "reportChars");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryStringEscapeFix();
  }

  private static class UnnecessaryStringEscapeFix extends InspectionGadgetsFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.string.escape.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
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
      if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        final StringBuilder newExpression = new StringBuilder();
        final PsiLiteralExpressionImpl literal = (PsiLiteralExpressionImpl)literalExpression;
        if (literal.getLiteralElementType() == JavaTokenType.TEXT_BLOCK_LITERAL) {
          int offset = 0;
          int start = findUnnecessarilyEscapedChars(text, 4);
          while (start >= 0) {
            newExpression.append(text, offset, start);
            offset = start + 2;
            final String escape = text.substring(start, offset);
            if ("\\n".equals(escape)) {
              final int indent = literal.getTextBlockIndent();
              newExpression.append('\n').append(StringUtil.repeatSymbol(' ', indent));
            }
            else {
              newExpression.append(escape.charAt(1));
            }
            start = findUnnecessarilyEscapedChars(text, offset);
          }
          newExpression.append(text.substring(offset));
        }
        else {
          int index = text.indexOf("\\'");
          int offset = 0;
          while (index > 0) {
            newExpression.append(text, offset, index);
            offset = index + 1;
            index = text.indexOf("\\'", offset);
          }
          newExpression.append(text.substring(offset));
        }
        PsiReplacementUtil.replaceExpression(literalExpression, newExpression.toString());
      }
      else if (PsiType.CHAR.equals(type) && text.equals("'\\\"'")) {
        PsiReplacementUtil.replaceExpression(literalExpression, "'\"'");
      }
    }
  }

  static int findUnnecessarilyEscapedChars(String text, int start) {
    boolean slash = false;
    int doubleQuotes = 0;
    final int max = text.length() - 3; // skip closing """
    for (int i = start; i < max; i++) {
      final char ch = text.charAt(i);
      if (ch == '\\') slash = !slash;
      else {
        if (slash) {
          if (ch == 'n' || ch == '\'') {
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
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        if (((PsiLiteralExpressionImpl)expression).getLiteralElementType() == JavaTokenType.TEXT_BLOCK_LITERAL) {
          final String text = expression.getText();
          int start = findUnnecessarilyEscapedChars(text, 4);
          while (start >= 0) {
            registerErrorAtOffset(expression, start, 2);
            start = findUnnecessarilyEscapedChars(text, start + 2);
          }
        }
        else {
          final String text = expression.getText();
          boolean slash = false;
          final int max = text.length() - 1; // skip closing "
          for (int i = 1; i < max; i++) {
            final char c = text.charAt(i);
            if (c == '\\') slash = !slash;
            else if (c == '\'' && slash) registerErrorAtOffset(expression, i - 1, 2);
            else slash = false;
          }
        }
      }
      else if (reportChars && PsiType.CHAR.equals(type)) {
        if ("'\\\"'".equals(expression.getText())) {
          registerErrorAtOffset(expression, 1, 2);
        }
      }
    }
  }
}
