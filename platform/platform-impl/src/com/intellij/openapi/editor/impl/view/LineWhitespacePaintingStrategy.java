// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.EditorSettings;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;


final class LineWhitespacePaintingStrategy {

  private static final char IDEOGRAPHIC_SPACE = '\u3000';
  private static final String WHITESPACE_CHARS = " \t" + IDEOGRAPHIC_SPACE;

  private final boolean whitespaceShown;
  private final boolean leadingWhitespaceShown;
  private final boolean innerWhitespaceShown;
  private final boolean trailingWhitespaceShown;
  private final boolean selectionWhitespaceShown;

  // Offsets on current line where leading whitespace ends and trailing whitespace starts correspondingly.
  private int currentLeadingEdge;
  private int currentTrailingEdge;

  LineWhitespacePaintingStrategy(@NotNull EditorSettings settings) {
    whitespaceShown = settings.isWhitespacesShown();
    leadingWhitespaceShown = settings.isLeadingWhitespaceShown();
    innerWhitespaceShown = settings.isInnerWhitespaceShown();
    trailingWhitespaceShown = settings.isTrailingWhitespaceShown();
    selectionWhitespaceShown = settings.isSelectionWhitespaceShown();
  }

  boolean showAnyWhitespace() {
    if (whitespaceShown) {
      if (leadingWhitespaceShown || innerWhitespaceShown || trailingWhitespaceShown || selectionWhitespaceShown) {
        return true;
      }
    }
    return false;
  }

  void update(CharSequence chars, int lineStart, int lineEnd) {
    if (showAnyWhitespace() && !(leadingWhitespaceShown && innerWhitespaceShown && trailingWhitespaceShown)) {
      currentTrailingEdge = CharArrayUtil.shiftBackward(chars, lineStart, lineEnd - 1, WHITESPACE_CHARS) + 1;
      currentLeadingEdge = CharArrayUtil.shiftForward(chars, lineStart, currentTrailingEdge, WHITESPACE_CHARS);
    }
  }

  boolean showWhitespaceAtOffset(int offset, CaretDataInView caretData) {
    if (!whitespaceShown) {
      return false;
    }
    if (offset < currentLeadingEdge
        ? leadingWhitespaceShown
        : offset >= currentTrailingEdge
          ? trailingWhitespaceShown
          : innerWhitespaceShown) {
      return true;
    } else {
      return selectionWhitespaceShown && caretData != null && caretData.isOffsetInSelection(offset);
    }
  }
}
