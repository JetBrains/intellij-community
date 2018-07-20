/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ipp.unicode;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementEditorPredicate;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UnicodeUnescapeIntention extends Intention {

  @Override
  protected void processIntention(@NotNull PsiElement element) {}

  @Override
  protected void processIntention(Editor editor, @NotNull PsiElement element) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      // does not check if Unicode escape is inside char or string literal (garbage in, garbage out)
      final Document document = editor.getDocument();
      final int start = selectionModel.getSelectionStart();
      final int end = selectionModel.getSelectionEnd();
      final String text = document.getText(new TextRange(start, end));
      final int textLength = end - start;
      final StringBuilder replacement = new StringBuilder(textLength);
      int anchor = 0;
      while (true) {
        final int index = indexOfUnicodeEscape(text, anchor + 1);
        if (index < 0) {
          break;
        }
        replacement.append(text, anchor, index);
        int hexStart = index + 1;
        while (text.charAt(hexStart) == 'u') {
          hexStart++;
        }
        anchor = hexStart + 4;
        final int c = Integer.parseInt(text.substring(hexStart, anchor), 16);
        replacement.appendCodePoint(c);
      }
      replacement.append(text, anchor, textLength);
      document.replaceString(start, end, replacement);
    }
    else {
      final CaretModel caretModel = editor.getCaretModel();
      final Document document = editor.getDocument();
      final int lineNumber = document.getLineNumber(caretModel.getOffset());
      final int lineStartOffset = document.getLineStartOffset(lineNumber);
      final String line = document.getText(new TextRange(lineStartOffset, document.getLineEndOffset(lineNumber)));
      final int column = caretModel.getLogicalPosition().column;
      final int index1 = indexOfUnicodeEscape(line, column);
      final int index2 = indexOfUnicodeEscape(line, column + 1);
      // if the caret is between two unicode escapes, replace the one to the right
      final int escapeStart = index2 == column ? index2 : index1;
      int hexStart = escapeStart + 1;
      while (line.charAt(hexStart) == 'u') {
        hexStart++;
      }
      final char c = (char)Integer.parseInt(line.substring(hexStart, hexStart + 4), 16);
      if (Character.isHighSurrogate(c)) {
        hexStart += 4;
        if (line.charAt(hexStart++) == '\\' && line.charAt(hexStart++) == 'u') {
          while (line.charAt(hexStart) == 'u') hexStart++;
          final char d = (char)Integer.parseInt(line.substring(hexStart, hexStart + 4), 16);
          document.replaceString(lineStartOffset + escapeStart, lineStartOffset + hexStart + 4, String.valueOf(new char[] {c, d}));
          return;
        }
      }
      else if (Character.isLowSurrogate(c)) {
        if (escapeStart >= 6 &&
            StringUtil.isHexDigit(line.charAt(escapeStart - 1)) && StringUtil.isHexDigit(line.charAt(escapeStart - 2)) &&
            StringUtil.isHexDigit(line.charAt(escapeStart - 3)) && StringUtil.isHexDigit(line.charAt(escapeStart - 4))) {
          int i = escapeStart - 5;
          while (i > 0 && line.charAt(i) == 'u') i--;
          if (line.charAt(i) == '\\' && (i == 0 || line.charAt(i - 1) != '\\')) {
            final char b = (char)Integer.parseInt(line.substring(escapeStart - 4, escapeStart), 16);
            document.replaceString(lineStartOffset + i, lineStartOffset + hexStart + 4, String.valueOf(new char[] {b, c}));
            return;
          }
        }
      }
      document.replaceString(lineStartOffset + escapeStart, lineStartOffset + hexStart + 4, String.valueOf(c));
    }
  }

  /**
   * see JLS 3.3. Unicode Escapes
   */
  static int indexOfUnicodeEscape(@NotNull String text, int offset) {
    final int length = text.length();
    for (int i = 0; i < length; i++) {
      final char c = text.charAt(i);
      if (c != '\\') {
        continue;
      }
      boolean isEscape = true;
      int previousChar = i - 1;
      while (previousChar >= 0 && text.charAt(previousChar) == '\\') {
        isEscape = !isEscape;
        previousChar--;
      }
      if (!isEscape) {
        continue;
      }
      int nextChar = i;
      do {
        nextChar++;
        if (nextChar >= length) {
          break;
        }
      }
      while (text.charAt(nextChar) == 'u'); // \uuuu0061 is a legal unicode escape
      if (nextChar == i + 1 || nextChar + 3 >= length) {
        break;
      }
      if (StringUtil.isHexDigit(text.charAt(nextChar)) &&
          StringUtil.isHexDigit(text.charAt(nextChar + 1)) &&
          StringUtil.isHexDigit(text.charAt(nextChar + 2)) &&
          StringUtil.isHexDigit(text.charAt(nextChar + 3))) {
        final int escapeEnd = nextChar + 4;
        if (offset <= escapeEnd) {
          final char d = (char)Integer.parseInt(text.substring(nextChar, nextChar + 4), 16);
          if (d == '\r') return -1; // carriage return not allowed
          return i;
        }
      }
    }
    return -1;
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new UnicodeEscapePredicate();
  }

  private static class UnicodeEscapePredicate extends PsiElementEditorPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element, @Nullable Editor editor) {
      if (editor == null) {
        return false;
      }
      final SelectionModel selectionModel = editor.getSelectionModel();
      final Document document = editor.getDocument();
      if (selectionModel.hasSelection()) {
        final int start = selectionModel.getSelectionStart();
        final int end = selectionModel.getSelectionEnd();
        if (start < 0 || end < 0 || start > end) {
          // shouldn't happen but http://ea.jetbrains.com/browser/ea_problems/50192
          return false;
        }
        final String text = document.getCharsSequence().subSequence(start, end).toString();
        return indexOfUnicodeEscape(text, 1) >= 0;
      }
      else {
        final CaretModel caretModel = editor.getCaretModel();
        final int lineNumber = document.getLineNumber(caretModel.getOffset());
        final String line = document.getText(new TextRange(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber)));
        final int column = caretModel.getLogicalPosition().column;
        final int index = indexOfUnicodeEscape(line, column);
        return index >= 0 && column >= index;
      }
    }
  }
}
