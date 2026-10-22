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
 * <p>
 * An {@code offset} is an offset in the {@link EditorAccessibleText}, and a {@code documentOffset} is an offset in the document.
 */
final class EditorAccessibleTextNavigation {
  /** Looking for text before the given offset */
  static final int BEFORE = -1;
  /** Looking for text at the given offset */
  static final int HERE = 0;
  /** Looking for text after the given offset */
  static final int AFTER = 1;

  @MagicConstant(intValues = {BEFORE, HERE, AFTER})
  @interface Direction {}

  @MagicConstant(intValues = {CHARACTER, WORD, SENTENCE})
  @interface TextPart {}

  @MagicConstant(intValues = {CHARACTER, WORD, SENTENCE, LINE, ATTRIBUTE_RUN})
  @interface ExtendedTextPart {}

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

  @Nullable String getTextAtOffset(@TextPart int type, int offset, @Direction int direction) {
    if (offset < 0 || offset >= myAccessibleText.getLength()) {
      return null;
    }
    switch (type) {
      case CHARACTER, WORD, SENTENCE -> {
        AccessibleTextSequence sequence = getSequenceAtIndex(type, offset, direction);
        return sequence == null ? null : sequence.text;
      }
      case LINE, ATTRIBUTE_RUN -> {
        // Not expected to be called in this method!
        assert false : type;
      }
    }
    return null;
  }

  /**
   * Similar to {@link #getTextAtOffset} but returns an {@link AccessibleTextSequence} and can accept a few more types.
   */
  @Nullable AccessibleTextSequence getSequenceAtIndex(@ExtendedTextPart int type, int offset, @Direction int direction) {
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

  private int moveLineOffset(int offset, @Direction int direction) {
    return switch (direction) {
      case AFTER -> {
        int lineNumber = myAccessibleText.getLineNumber(offset) + 1;
        yield lineNumber == myAccessibleText.getLineCount() ? -1 : myAccessibleText.getLineStartOffset(lineNumber);
      }
      case BEFORE -> {
        int lineNumber = myAccessibleText.getLineNumber(offset) - 1;
        yield lineNumber < 0 ? -1 : myAccessibleText.getLineStartOffset(lineNumber);
      }
      default -> {
        assert direction == HERE;
        yield offset;
      }
    };
  }

  private int getLineAtOffsetStart(int offset, @Direction int direction) {
    offset = moveLineOffset(offset, direction);
    if (offset == -1) {
      return -1;
    }

    return myAccessibleText.getLineStartOffset(myAccessibleText.getLineNumber(offset));
  }

  private int getLineAtOffsetEnd(int offset, @Direction int direction) {
    offset = moveLineOffset(offset, direction);
    if (offset == -1) {
      return -1;
    }

    return myAccessibleText.getLineEndOffset(myAccessibleText.getLineNumber(offset));
  }

  private CaretStopPolicy resolveCaretStopPolicy(@Direction int direction) {
    var caretStopOptions = editor.getSettings().getCaretStopOptions();
    return switch (direction) {
      case BEFORE -> caretStopOptions.getBackwardPolicy();
      case HERE -> isLastCaretMoveForward.getAsBoolean() ? caretStopOptions.getForwardPolicy() : caretStopOptions.getBackwardPolicy();
      default -> caretStopOptions.getForwardPolicy();
    };
  }

  private AccessibleTextSequence getWordOrLexeme(int offset, @Direction int direction) {
    if (direction == HERE) {
      TextRange placeholder = myAccessibleText.getPlaceholderRange(offset);
      if (placeholder != null) {
        int start = placeholder.getStartOffset();
        int end = placeholder.getEndOffset();
        return new AccessibleTextSequence(start, end, myAccessibleText.getText(start, end));
      }
    }
    boolean isCamel = editor.getSettings().isCamelWords();
    var caretStopPolicy = resolveCaretStopPolicy(direction);
    int documentOffset = moveWordOffset(myAccessibleText.toDocumentOffset(offset), direction, caretStopPolicy, isCamel);
    var wordStop = caretStopPolicy.getWordStop();
    int wordStart = getWordAtOffsetStart(documentOffset, wordStop, isCamel);
    int wordEnd = getWordAtOffsetEnd(documentOffset, wordStop, isCamel);
    if (wordStart == -1 || wordEnd == -1 || wordStart > wordEnd) {
      return null;
    }
    int start = myAccessibleText.fromDocumentOffset(wordStart);
    int end = myAccessibleText.fromDocumentOffset(wordEnd);
    return new AccessibleTextSequence(start, end, myAccessibleText.getText(start, end));
  }


  private int moveWordOffset(int documentOffset,
                             @Direction int direction,
                             CaretStopPolicy stopPolicy,
                             boolean isCamel) {
    if (direction == AFTER) {
      return EditorActionUtil.getNextCaretStopOffset(editor, stopPolicy, isCamel);
    }
    if (direction == BEFORE) {
      return EditorActionUtil.getPreviousCaretStopOffset(editor, stopPolicy, isCamel);
    }
    return documentOffset;
  }

  private int getDocumentLineStart(int documentOffset) {
    Document document = editor.getDocument();
    if (documentOffset == 0) {
      return 0;
    }
    int lineNumber = editor.offsetToLogicalPosition(documentOffset).line;
    return document.getLineStartOffset(lineNumber);
  }

  private int getWordAtOffsetStart(int documentOffset, CaretStop wordStop, boolean isCamel) {
    if (documentOffset == 0) {
      return 0;
    }
    if (wordStop.isAtEnd() && !wordStop.isAtStart()) {
      if (EditorActionUtil.isWordOrLexemeEnd(editor, documentOffset, isCamel)) {
        return getWordAtOffsetStart(documentOffset - 1, isCamel);
      }
      if (documentOffset == getDocumentLineStart(documentOffset)) {
        return documentOffset - 1;
      }
    }
    if (Character.isWhitespace(editor.getDocument().getText().charAt(documentOffset))) {
      return documentOffset;
    }
    return getWordAtOffsetStart(documentOffset, isCamel);
  }

  // Based on CaretImpl#getWordAtCaretStart
  private int getWordAtOffsetStart(int documentOffset, boolean isCamel) {
    Document document = editor.getDocument();
    if (documentOffset == 0) {
      return 0;
    }
    int lineNumber = editor.offsetToLogicalPosition(documentOffset).line;
    int newOffset = documentOffset;
    int minOffset = lineNumber > 0 ? document.getLineEndOffset(lineNumber - 1) : 0;
    for (; newOffset > minOffset; newOffset--) {
      if (EditorActionUtil.isWordOrLexemeStart(editor, newOffset, isCamel)) {
        break;
      }
    }

    return newOffset;
  }

  private int getWordAtOffsetEnd(int documentOffset, CaretStop stopWord, boolean isCamel) {
    if (stopWord.isAtStart()) {
      if (Character.isWhitespace(editor.getDocument().getText().charAt(documentOffset))) {
        return documentOffset + 1;
      }
      return getWordAtOffsetEnd(documentOffset + 1, isCamel);
    }
    if (documentOffset == getDocumentLineStart(documentOffset)) {
      return documentOffset;
    }
    return getWordAtOffsetEnd(documentOffset, isCamel);
  }


  // Based on CaretImpl#getWordAtCaretEnd
  private int getWordAtOffsetEnd(int documentOffset, boolean isCamel) {
    Document document = editor.getDocument();
    if (documentOffset >= document.getTextLength() - 1 || document.getLineCount() == 0) {
      return documentOffset;
    }
    int newOffset = documentOffset;
    int lineNumber = editor.offsetToLogicalPosition(documentOffset).line;
    int maxOffset = document.getLineEndOffset(lineNumber);
    if (newOffset > maxOffset) {
      if (lineNumber + 1 >= document.getLineCount()) {
        return documentOffset;
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
