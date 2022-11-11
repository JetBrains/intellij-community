// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.unicode;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementEditorPredicate;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceOctalEscapeWithUnicodeEscapeIntention extends Intention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.octal.escape.with.unicode.escape.intention.family.name");
  }

  @Override
  public @NotNull String getText() {
    return IntentionPowerPackBundle.message("replace.octal.escape.with.unicode.escape.intention.name");
  }

  @Override
  protected void processIntention(@NotNull PsiElement element) {}

  @Override
  protected void processIntention(Editor editor, @NotNull PsiElement element) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      // does not check if octal escape is inside char or string literal (garbage in, garbage out)
      final Document document = editor.getDocument();
      final int start = selectionModel.getSelectionStart();
      final int end = selectionModel.getSelectionEnd();
      final String text = document.getText(new TextRange(start, end));
      final int textLength = end - start;
      final StringBuilder replacement = new StringBuilder(textLength);
      int anchor = 0;
      while (true) {
        final int index = indexOfOctalEscape(text, anchor + 1);
        if (index < 0) {
          break;
        }
        replacement.append(text, anchor, index);
        anchor = appendUnicodeEscape(text, index, replacement);
      }
      replacement.append(text, anchor, textLength);
      document.replaceString(start, end, replacement);
    }
    else if (element instanceof PsiLiteralExpression) {
      final PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
      final String text = literalExpression.getText();
      final CaretModel model = editor.getCaretModel();
      final int offset = model.getOffset() - literalExpression.getTextOffset();
      final StringBuilder newLiteralText = new StringBuilder();
      final int index1 = indexOfOctalEscape(text, offset);
      final int index2 = indexOfOctalEscape(text, offset + 1);
      final int escapeStart = index2 == offset ? index2 : index1;
      newLiteralText.append(text, 0, escapeStart);
      final int escapeEnd = appendUnicodeEscape(text, escapeStart, newLiteralText);
      newLiteralText.append(text.substring(escapeEnd));
      PsiReplacementUtil.replaceExpression(literalExpression, newLiteralText.toString());
    }
  }

  private static int appendUnicodeEscape(String text, int escapeStart, @NonNls StringBuilder out) {
    final int textLength = text.length();
    int length = 1;
    boolean zeroToThree = false;
    while (escapeStart + length < textLength) {
      final char c = text.charAt(escapeStart + length);
      if (length == 1 && (c == '0' || c == '1' || c == '2' || c == '3')) {
        zeroToThree = true;
      }
      if (c < '0' || c > '7' || length > 3 || (length > 2 && !zeroToThree)) {
        final int ch = Integer.parseInt(text.substring(escapeStart + 1, escapeStart + length), 8);
        out.append("\\u").append(String.format("%04x", Integer.valueOf(ch)));
        break;
      }
      length++;
    }
    return escapeStart + length;
  }

  private static int indexOfOctalEscape(String text, int offset) {
    final int textLength = text.length();
    int escapeStart = -1;
    outer: while (true) {
      escapeStart = text.indexOf('\\', escapeStart + 1);
      if (escapeStart < 0) {
        break;
      }
      if (escapeStart < offset - 4 || escapeStart < textLength - 1 && text.charAt(escapeStart + 1) == '\\') {
        continue;
      }
      boolean isEscape = true;
      int previousChar = escapeStart - 1;
      while (previousChar >= 0 && text.charAt(previousChar) == '\\') {
        isEscape = !isEscape;
        previousChar--;
      }
      if (!isEscape) {
        continue;
      }
      int length = 1;
      // see JLS 3.10.6. Escape Sequences for Character and String Literals
      boolean zeroToThree = false;
      while (escapeStart + length < textLength) {
        final char c = text.charAt(escapeStart + length);
        if (length == 1 && (c == '0' || c == '1' || c == '2' || c == '3')) {
          zeroToThree = true;
        }
        if (c < '0' || c > '7' || length > 3 || (length > 2 && !zeroToThree)) {
          if (offset <= escapeStart + length && length > 1) {
            return escapeStart;
          }
          continue outer;
        }
        length++;
      }
      return escapeStart;
    }
    return -1;
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new OctalEscapePredicate();
  }

  private static class OctalEscapePredicate extends PsiElementEditorPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element, @Nullable Editor editor) {
      if (editor == null) {
        return false;
      }
      final SelectionModel selectionModel = editor.getSelectionModel();
      if (selectionModel.hasSelection()) {
        final int start = selectionModel.getSelectionStart();
        final int end = selectionModel.getSelectionEnd();
        if (start < 0 || end < 0 || start > end) {
          // shouldn't happen but http://ea.jetbrains.com/browser/ea_problems/51155
          return false;
        }
        final String text = editor.getDocument().getCharsSequence().subSequence(start, end).toString();
        return indexOfOctalEscape(text, 1) >= 0;
      }
      else if (element instanceof PsiLiteralExpression) {
        final PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
        final String text = literalExpression.getText();
        final CaretModel model = editor.getCaretModel();
        final int offset = model.getOffset() - literalExpression.getTextOffset();
        final int index = indexOfOctalEscape(text, offset);
        return index >= 0 && offset >= index;
      }
      return false;
    }
  }
}
