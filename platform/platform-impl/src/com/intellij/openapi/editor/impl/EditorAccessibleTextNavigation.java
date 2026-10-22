// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.actions.CaretStop;
import com.intellij.openapi.editor.actions.CaretStopPolicy;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.util.TextRange;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleTextSequence;
import java.util.function.BooleanSupplier;

import static javax.accessibility.AccessibleExtendedText.ATTRIBUTE_RUN;
import static javax.accessibility.AccessibleExtendedText.LINE;
import static javax.accessibility.AccessibleText.CHARACTER;
import static javax.accessibility.AccessibleText.SENTENCE;
import static javax.accessibility.AccessibleText.WORD;

/**
 * Finds the text of a character, a word, or a line at an index of the {@link EditorAccessibleText}.
 * It can also find the text before or after that index.
 */
final class EditorAccessibleTextNavigation {
  /** Looking for text before the given offset */
  static final int BEFORE = -1;
  /** Looking for text at the given offset */
  static final int HERE = 0;
  /** Looking for text after the given offset */
  static final int AFTER = 1;

  private final EditorImpl editor;
  private final EditorAccessibleText myAccessibleText;
  private final BooleanSupplier isLastCaretMoveForward;

  EditorAccessibleTextNavigation(@NotNull EditorImpl editor,
                                 @NotNull EditorAccessibleText accessibleText,
                                 @NotNull BooleanSupplier isLastCaretMoveForward) {
    this.editor = editor;
    myAccessibleText = accessibleText;
    this.isLastCaretMoveForward = isLastCaretMoveForward;
  }

  @Nullable String getTextAtOffset(
    @MagicConstant(intValues = {CHARACTER, WORD, SENTENCE})
    int type,
    int offset,
    @MagicConstant(intValues = {BEFORE, HERE, AFTER})
    int direction) {
    int length = myAccessibleText.getLength();
    if (offset < 0 || offset >= length) {
      return null;
    }
    switch (type) {
      case CHARACTER: {
        if (offset + direction < length && offset + direction >= 0) {
          int startOffset = offset + direction;
          return myAccessibleText.getText(startOffset, startOffset + 1);
        }
        break;
      }

      case WORD: {
        var word = getWordOrLexeme(offset, direction);
        return word == null ? null : word.text;
      }

      case SENTENCE: {
        int lineStart = getLineAtOffsetStart(offset, direction);
        int lineEnd = getLineAtOffsetEnd(offset, direction);
        if (lineStart == -1 || lineEnd == -1) {
          return null;
        }
        return myAccessibleText.getText(lineStart, lineEnd);
      }

      case LINE:
      case ATTRIBUTE_RUN:
        // Not expected to be called in this method!
        assert false : type;

      default:
        break;
    }

    return null;
  }

  /**
   * Similar to {@link #getTextAtOffset} but returns an {@link AccessibleTextSequence} and can accept a few more types.
   */
  @Nullable AccessibleTextSequence getSequenceAtIndex(
      @MagicConstant(intValues = {
        CHARACTER,
        WORD,
        SENTENCE,
        LINE,
        ATTRIBUTE_RUN})
      int type,
      int offset,
      @MagicConstant(intValues = {BEFORE, HERE, AFTER})
      int direction) {
    assert direction == BEFORE || direction == HERE || direction == AFTER;

    int length = myAccessibleText.getLength();
    if (offset < 0 || offset >= length) {
      return null;
    }

    switch (type) {
      case CHARACTER -> {
        AccessibleTextSequence charSequence = null;
        if (offset + direction < length &&
            offset + direction >= 0) {
          int startOffset = offset + direction;
          charSequence = new AccessibleTextSequence(startOffset, startOffset + 1,
                                                    myAccessibleText.getText(startOffset, startOffset + 1));
        }
        return charSequence;
      }
      case ATTRIBUTE_RUN, WORD -> {
        return getWordOrLexeme(offset, direction);
      }
      case LINE, SENTENCE -> {
        int lineStart = getLineAtOffsetStart(offset, direction);
        int lineEnd = getLineAtOffsetEnd(offset, direction);
        if (lineStart == -1 || lineEnd == -1) {
          return null;
        }

        return new AccessibleTextSequence(lineStart, lineEnd, myAccessibleText.getText(lineStart, lineEnd));
      }
    }
    return null;
  }

  private int getLineAtOffsetStart(int offset) {
    Document document = editor.getDocument();
    if (offset == 0) {
      return 0;
    }
    int lineNumber = editor.offsetToLogicalPosition(offset).line;
    return document.getLineStartOffset(lineNumber);
  }

  private int moveLineOffset(int offset, @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
    if (direction == AFTER) {
      int lineNumber = myAccessibleText.getLineNumber(offset);
      lineNumber++;
      if (lineNumber == myAccessibleText.getLineCount()) {
        return -1;
      }
      return myAccessibleText.getLineStartOffset(lineNumber);
    } else if (direction == BEFORE) {
      int lineNumber = myAccessibleText.getLineNumber(offset);
      lineNumber--;
      if (lineNumber < 0) {
        return -1;
      }
      return myAccessibleText.getLineStartOffset(lineNumber);
    } else {
      assert direction == HERE;
      return offset;
    }
  }

  private int getLineAtOffsetStart(int offset, @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
    offset = moveLineOffset(offset, direction);
    if (offset == -1) {
      return -1;
    }

    return myAccessibleText.getLineStartOffset(myAccessibleText.getLineNumber(offset));
  }

  private int getLineAtOffsetEnd(int offset, @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
    offset = moveLineOffset(offset, direction);
    if (offset == -1) {
      return -1;
    }

    return myAccessibleText.getLineEndOffset(myAccessibleText.getLineNumber(offset));
  }

  private CaretStopPolicy resolveCaretStopPolicy(@MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
    var caretStopOptions = editor.getSettings().getCaretStopOptions();
    switch (direction) {
      case AFTER -> {
        return caretStopOptions.getForwardPolicy();
      }
      case BEFORE -> {
        return caretStopOptions.getBackwardPolicy();
      }
      case HERE -> {
        return isLastCaretMoveForward.getAsBoolean() ? caretStopOptions.getForwardPolicy() : caretStopOptions.getBackwardPolicy();
      }
    }
    return caretStopOptions.getForwardPolicy();
  }

  private AccessibleTextSequence getWordOrLexeme(int offset, @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction) {
    if (direction == HERE) {
      TextRange placeholder = myAccessibleText.getPlaceholderRange(offset);
      if (placeholder != null) {
        int start = placeholder.getStartOffset();
        int end = placeholder.getEndOffset();
        return new AccessibleTextSequence(start, end, myAccessibleText.getText(start, end));
      }
    }
    // the word helpers below use document offsets
    boolean isCamel = editor.getSettings().isCamelWords();
    var caretStopPolicy = resolveCaretStopPolicy(direction);
    offset = moveWordOffset(myAccessibleText.toDocumentOffset(offset), direction, caretStopPolicy, isCamel);
    var wordStop = caretStopPolicy.getWordStop();
    int wordStart = getWordAtOffsetStart(offset, wordStop, isCamel);
    int wordEnd = getWordAtOffsetEnd(offset, wordStop, isCamel);
    if (wordStart == -1 || wordEnd == -1 || wordStart > wordEnd) {
      return null;
    }
    int start = myAccessibleText.fromDocumentOffset(wordStart);
    int end = myAccessibleText.fromDocumentOffset(wordEnd);
    return new AccessibleTextSequence(start, end, myAccessibleText.getText(start, end));
  }


  private int moveWordOffset(int offset,
                             @MagicConstant(intValues = {BEFORE, HERE, AFTER}) int direction,
                             CaretStopPolicy stopPolicy,
                             boolean isCamel) {
    if (direction == AFTER) {
      return EditorActionUtil.getNextCaretStopOffset(editor, stopPolicy, isCamel);
    }
    if (direction == BEFORE) {
      return EditorActionUtil.getPreviousCaretStopOffset(editor, stopPolicy, isCamel);
    }
    return offset;
  }

  private int getWordAtOffsetStart(int offset, CaretStop wordStop, boolean isCamel) {
    if (offset == 0) {
      return 0;
    }
    if (wordStop.isAtEnd() && !wordStop.isAtStart()) {
      if (EditorActionUtil.isWordOrLexemeEnd(editor, offset, isCamel)) {
        return getWordAtOffsetStart(offset - 1, isCamel);
      }
      if (offset == getLineAtOffsetStart(offset)) {
        return offset - 1;
      }
    }
    if (Character.isWhitespace(editor.getDocument().getText().charAt(offset))) {
      return offset;
    }
    return getWordAtOffsetStart(offset, isCamel);
  }

  // Based on CaretImpl#getWordAtCaretStart
  private int getWordAtOffsetStart(int offset, boolean isCamel) {
    Document document = editor.getDocument();
    if (offset == 0) {
      return 0;
    }
    int lineNumber = editor.offsetToLogicalPosition(offset).line;
    int newOffset = offset;
    int minOffset = lineNumber > 0 ? document.getLineEndOffset(lineNumber - 1) : 0;
    for (; newOffset > minOffset; newOffset--) {
      if (EditorActionUtil.isWordOrLexemeStart(editor, newOffset, isCamel)) {
        break;
      }
    }

    return newOffset;
  }

  private int getWordAtOffsetEnd(int offset, CaretStop stopWord, boolean isCamel) {
    if (stopWord.isAtStart()) {
      if (Character.isWhitespace(editor.getDocument().getText().charAt(offset))) {
        return offset + 1;
      }
      return getWordAtOffsetEnd(offset + 1, isCamel);
    }
    if (offset == getLineAtOffsetStart(offset)) {
      return offset;
    }
    return getWordAtOffsetEnd(offset, isCamel);
  }


  // Based on CaretImpl#getWordAtCaretEnd
  private int getWordAtOffsetEnd(int offset, boolean isCamel) {
    Document document = editor.getDocument();
    if (offset >= document.getTextLength() - 1 || document.getLineCount() == 0) {
      return offset;
    }
    int newOffset = offset;
    int lineNumber = editor.offsetToLogicalPosition(offset).line;
    int maxOffset = document.getLineEndOffset(lineNumber);
    if (newOffset > maxOffset) {
      if (lineNumber + 1 >= document.getLineCount()) {
        return offset;
      }
      maxOffset = document.getLineEndOffset(lineNumber + 1);
    }
    for (; newOffset < maxOffset; newOffset++) {
      if (EditorActionUtil.isWordOrLexemeEnd(editor, newOffset, isCamel)) {
        break;
      }
    }

    return newOffset;
  }
}
