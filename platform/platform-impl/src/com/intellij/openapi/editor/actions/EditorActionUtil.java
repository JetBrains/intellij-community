// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FoldingModelImpl;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static java.lang.Character.*;

public final class EditorActionUtil {
  public static final Object EDIT_COMMAND_GROUP = Key.create("EditGroup");
  public static final Object DELETE_COMMAND_GROUP = Key.create("DeleteGroup");

  private EditorActionUtil() {
  }

  /**
   * Tries to change given editor's viewport position in vertical dimension by the given number of visual lines.
   *
   * @param editor     target editor which viewport position should be changed
   * @param lineShift  defines viewport position's vertical change length
   * @param columnShift  defines viewport position's horizontal change length
   * @param moveCaret  flag that identifies whether caret should be moved if its current position becomes off-screen
   */
  public static void scrollRelatively(@NotNull Editor editor, int lineShift, int columnShift, boolean moveCaret) {
    if (lineShift != 0) {
      editor.getScrollingModel().scrollVertically(
        editor.getScrollingModel().getVerticalScrollOffset() + lineShift * editor.getLineHeight()
      );
    }
    if (columnShift != 0) {
      editor.getScrollingModel().scrollHorizontally(
        editor.getScrollingModel().getHorizontalScrollOffset() + columnShift * EditorUtil.getSpaceWidth(Font.PLAIN, editor)
      );
    }

    if (!moveCaret) {
      return;
    }

    Rectangle viewRectangle = getVisibleArea(editor);
    int lineNumber = editor.getCaretModel().getVisualPosition().line;
    VisualPosition startPos = editor.xyToVisualPosition(new Point(0, viewRectangle.y));
    int start = startPos.line + 1;
    VisualPosition endPos = editor.xyToVisualPosition(new Point(0, viewRectangle.y + viewRectangle.height));
    int end = endPos.line - 2;
    if (lineNumber < start) {
      editor.getCaretModel().moveCaretRelatively(0, start - lineNumber, false, false, true);
    }
    else if (lineNumber > end) {
      editor.getCaretModel().moveCaretRelatively(0, end - lineNumber, false, false, true);
    }
  }

  public static void moveCaretRelativelyAndScroll(@NotNull Editor editor,
                                                  int columnShift,
                                                  int lineShift,
                                                  boolean withSelection) {
    Rectangle visibleArea = getVisibleArea(editor);
    VisualPosition pos = editor.getCaretModel().getVisualPosition();
    Point caretLocation = editor.visualPositionToXY(pos);
    int caretVShift = caretLocation.y - visibleArea.y;

    editor.getCaretModel().moveCaretRelatively(columnShift, lineShift, withSelection, false, false);

    VisualPosition caretPos = editor.getCaretModel().getVisualPosition();
    Point caretLocation2 = editor.visualPositionToXY(caretPos);
    final boolean scrollToCaret = !(editor instanceof EditorImpl) || ((EditorImpl)editor).isScrollToCaret();
    if (scrollToCaret) {
      editor.getScrollingModel().scrollVertically(caretLocation2.y - caretVShift);
    }
  }

  public static void indentLine(Project project, @NotNull Editor editor, int lineNumber, int indent) {
    int caretOffset = editor.getCaretModel().getOffset();
    int newCaretOffset = indentLine(project, editor, lineNumber, indent, caretOffset);
    editor.getCaretModel().moveToOffset(newCaretOffset);
  }

  // This method avoid moving caret directly, so it's suitable for invocation in bulk mode.
  // It does calculate (and returns) target caret position.
  public static int indentLine(Project project, @NotNull Editor editor, int lineNumber, int indent, int caretOffset) {
    return EditorCoreUtil.indentLine(project, editor, lineNumber, indent, caretOffset, shouldUseSmartTabs(project, editor));
  }

  public static boolean shouldUseSmartTabs(Project project, @NotNull Editor editor) {
    if (!(editor instanceof EditorEx)) return false;
    return CodeStyle.getIndentOptions(project, editor.getDocument()).SMART_TABS;
  }

  /**
   * Selects the entire lines covering the current selection, if any.
   * If there's no selection, selects a single line of text at the caret position.
   * Because the resulting selection always includes the line ending character,
   * repeated invocations of this method extend the selection to include each next line one by one.
   */
  public static void selectEntireLines(@NotNull Caret caret) {
    selectEntireLines(caret, false);
  }

  /**
   * Selects the entire lines covering the current selection, if any.
   * If there's no selection, or 'resetToSingleLineAtCaret' is true, selects a single line of text at the caret position.
   * Unless 'resetToSingleLineAtCaret' is set, and because the resulting selection always includes the line ending character,
   * repeated invocations of this method extend the selection to include each next line one by one.
   *
   * @param resetToSingleLineAtCaret discard the current selection, if any,
   *                                 and select just a single line at the caret position.
   */
  public static void selectEntireLines(@NotNull Caret caret, boolean resetToSingleLineAtCaret) {
    Editor editor = caret.getEditor();
    int lineNumber = caret.getLogicalPosition().line;
    Document document = editor.getDocument();
    if (lineNumber >= document.getLineCount()) {
      return;
    }
    TextRange range =
      EditorUtil.calcSurroundingTextRange(editor,
                                          resetToSingleLineAtCaret ? caret.getVisualPosition() : caret.getSelectionStartPosition(),
                                          resetToSingleLineAtCaret ? caret.getVisualPosition() : caret.getSelectionEndPosition());
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    caret.setSelection(range.getStartOffset(), range.getEndOffset());
  }

  public static @NotNull TextRange getRangeToWordEnd(@NotNull Editor editor, boolean isCamel, boolean handleQuoted) {
    int startOffset = editor.getCaretModel().getOffset();
    // IDEA-211756 "Delete to word end" is extremely inconvenient on whitespaces
    int endOffset = getNextCaretStopOffset(editor, CaretStopPolicy.BOTH, isCamel, handleQuoted);
    return TextRange.create(startOffset, endOffset);
  }

  public static @NotNull TextRange getRangeToWordStart(@NotNull Editor editor, boolean isCamel, boolean handleQuoted) {
    int endOffset = editor.getCaretModel().getOffset();
    int startOffset = getPreviousCaretStopOffset(editor, CaretStopPolicy.WORD_START, isCamel, handleQuoted);
    return TextRange.create(startOffset, endOffset);
  }

  @ApiStatus.Internal
  public static int getNextCaretStopOffset(@NotNull Editor editor, @NotNull CaretStopPolicy caretStopPolicy, boolean isCamel) {
    return getNextCaretStopOffset(editor, caretStopPolicy, isCamel, false);
  }

  @ApiStatus.Internal
  public static int getPreviousCaretStopOffset(@NotNull Editor editor, @NotNull CaretStopPolicy caretStopPolicy, boolean isCamel) {
    return getPreviousCaretStopOffset(editor, caretStopPolicy, isCamel, false);
  }

  @SuppressWarnings("Duplicates")
  @ApiStatus.Internal
  public static int getNextCaretStopOffset(@NotNull Editor editor, @NotNull CaretStopPolicy caretStopPolicy,
                                           boolean isCamel, boolean handleQuoted) {
    int maxOffset = getNextLineStopOffset(editor, caretStopPolicy.getLineStop());

    final CaretStop wordStop = caretStopPolicy.getWordStop();
    if (wordStop.equals(CaretStop.NONE)) return maxOffset;

    final int offset = editor.getCaretModel().getOffset();
    if (offset == maxOffset) return maxOffset;

    final CharSequence text = editor.getDocument().getCharsSequence();
    final HighlighterIterator tokenIterator = createHighlighterIteratorAtOffset(editor, offset);

    final int newOffset = getNextWordStopOffset(text, wordStop, tokenIterator, offset, maxOffset, isCamel);
    if (newOffset < maxOffset &&
        handleQuoted && !tokenIterator.atEnd() &&
        isTokenStart(tokenIterator, newOffset - 1) &&
        isQuotedToken(tokenIterator, text)) {
      // now at the end of an opening quote: | "word" -> "|word"
      // find the start of a closing quote:   "|word" -> "word|"  (must be only a single step away)
      final int newOffsetBeforeQuote = getNextWordStopOffset(text, CaretStop.BOTH, tokenIterator, newOffset, maxOffset, isCamel);
      if (isTokenEnd(tokenIterator, newOffsetBeforeQuote + 1)) {
        return getNextWordStopOffset(text, wordStop, tokenIterator, newOffsetBeforeQuote, maxOffset, isCamel); // "word"|
      }
    }
    return newOffset;
  }

  @SuppressWarnings("Duplicates")
  @ApiStatus.Internal
  public static int getPreviousCaretStopOffset(@NotNull Editor editor, @NotNull CaretStopPolicy caretStopPolicy,
                                               boolean isCamel, boolean handleQuoted) {
    int minOffset = getPreviousLineStopOffset(editor, caretStopPolicy.getLineStop());

    final CaretStop wordStop = caretStopPolicy.getWordStop();
    if (wordStop.equals(CaretStop.NONE)) return minOffset;

    final int offset = editor.getCaretModel().getOffset();
    if (offset == minOffset) return minOffset;

    final CharSequence text = editor.getDocument().getCharsSequence();
    final HighlighterIterator tokenIterator = createHighlighterIteratorAtOffset(editor, offset - 1);

    final int newOffset = getPreviousWordStopOffset(text, wordStop, tokenIterator, offset, minOffset, isCamel);
    if (newOffset > minOffset &&
        handleQuoted && !tokenIterator.atEnd() &&
        isTokenEnd(tokenIterator, newOffset + 1) &&
        isQuotedToken(tokenIterator, text)) {
      // at the start of a closing quote:  "word|" <- "word" |
      // find the end of an opening quote: "|word" <- "word|"  (must be only a single step away)
      final int newOffsetAfterQuote = getPreviousWordStopOffset(text, CaretStop.BOTH, tokenIterator, newOffset, minOffset, isCamel);
      if (isTokenStart(tokenIterator, newOffsetAfterQuote - 1)) {
        return getPreviousWordStopOffset(text, wordStop, tokenIterator, newOffsetAfterQuote, minOffset, isCamel); // |"word"
      }
    }
    return newOffset;
  }

  private static int getNextWordStopOffset(@NotNull CharSequence text, @NotNull CaretStop wordStop,
                                           @NotNull HighlighterIterator tokenIterator,
                                           int offset, int maxOffset, boolean isCamel) {
    int newOffset = offset + 1;
    for (; newOffset < maxOffset; newOffset++) {
      final boolean isTokenBoundary = advanceTokenOnBoundary(tokenIterator, text, newOffset);
      if (isWordStopOffset(text, wordStop, newOffset, isCamel, isTokenBoundary)) break;
    }
    return newOffset;
  }

  private static int getPreviousWordStopOffset(@NotNull CharSequence text, @NotNull CaretStop wordStop,
                                               @NotNull HighlighterIterator tokenIterator,
                                               int offset, int minOffset, boolean isCamel) {
    int newOffset = offset - 1;
    for (; newOffset > minOffset; newOffset--) {
      final boolean isTokenBoundary = retreatTokenOnBoundary(tokenIterator, text, newOffset);
      if (isWordStopOffset(text, wordStop, newOffset, isCamel, isTokenBoundary)) break;
    }
    return newOffset;
  }

  private static boolean isWordStopOffset(@NotNull CharSequence text, @NotNull CaretStop wordStop,
                                          int offset, boolean isCamel, boolean isLexemeBoundary) {
    if (wordStop.isAtStart() && wordStop.isAtEnd()) {
      return isLexemeBoundary ||
             isWordStart(text, offset, isCamel) ||
             isWordEnd(text, offset, isCamel);
    }
    if (wordStop.isAtStart()) return isLexemeBoundary && !isWordEnd(text, offset, isCamel) || isWordStart(text, offset, isCamel);
    if (wordStop.isAtEnd()) return isLexemeBoundary && !isWordStart(text, offset, isCamel) || isWordEnd(text, offset, isCamel);
    return false;
  }

  private static boolean advanceTokenOnBoundary(@NotNull HighlighterIterator tokenIterator, @NotNull CharSequence text, int offset) {
    if (tokenIterator.atEnd()) return false;
    if (isTokenEnd(tokenIterator, offset)) {
      final IElementType leftToken = tokenIterator.getTokenType();
      final boolean wasQuotedToken = isQuotedToken(tokenIterator, text);
      tokenIterator.advance();
      if (wasQuotedToken) return true;
      if (tokenIterator.atEnd()) return false;
      return isQuotedToken(tokenIterator, text) ||
             !isBetweenWhitespaces(text, offset) && isLexemeBoundary(leftToken, tokenIterator.getTokenType());
    }
    return isQuotedTokenInnardsBoundary(tokenIterator, text, offset);
  }

  private static boolean retreatTokenOnBoundary(@NotNull HighlighterIterator tokenIterator, @NotNull CharSequence text, int offset) {
    if (tokenIterator.atEnd()) return false;
    if (isTokenStart(tokenIterator, offset)) {
      final IElementType rightToken = tokenIterator.getTokenType();
      final boolean wasQuotedToken = isQuotedToken(tokenIterator, text);
      tokenIterator.retreat();
      if (wasQuotedToken) return true;
      if (tokenIterator.atEnd()) return false;
      return isQuotedToken(tokenIterator, text) ||
             !isBetweenWhitespaces(text, offset) && isLexemeBoundary(tokenIterator.getTokenType(), rightToken);
    }
    return isQuotedTokenInnardsBoundary(tokenIterator, text, offset);
  }

  private static boolean isQuotedTokenInnardsBoundary(@NotNull HighlighterIterator tokenIterator, @NotNull CharSequence text, int offset) {
    return (isTokenStart(tokenIterator, offset - 1) ||
            isTokenEnd(tokenIterator, offset + 1)) &&
           isQuotedToken(tokenIterator, text);
  }

  private static boolean isTokenStart(@NotNull HighlighterIterator tokenIterator, int offset) {
    return offset == tokenIterator.getStart();
  }

  private static boolean isTokenEnd(@NotNull HighlighterIterator tokenIterator, int offset) {
    return offset == tokenIterator.getEnd();
  }

  private static boolean isQuotedToken(@NotNull HighlighterIterator tokenIterator, @NotNull CharSequence text) {
    final int startOffset = tokenIterator.getStart();
    final int endOffset = tokenIterator.getEnd();
    if (endOffset - startOffset < 2) return false;
    final char openingQuote = getQuoteAt(text, startOffset);
    final char closingQuote = getQuoteAt(text, endOffset - 1);
    return openingQuote != 0 && closingQuote == openingQuote;
  }

  private static char getQuoteAt(@NotNull CharSequence text, int offset) {
    if (offset < 0 || offset >= text.length()) return 0;
    final char ch = text.charAt(offset);
    return (ch == '\'' || ch == '\"') ? ch : 0;
  }

  private static @NotNull HighlighterIterator createHighlighterIteratorAtOffset(@NotNull Editor editor, int offset) {
    return editor.getHighlighter().createIterator(offset);
  }

  private static boolean isLexemeBoundary(@Nullable IElementType leftTokenType,
                                          @Nullable IElementType rightTokenType) {
    return leftTokenType != null &&
           rightTokenType != null &&
           LanguageWordBoundaryFilter.getInstance().forLanguage(rightTokenType.getLanguage())
             .isWordBoundary(leftTokenType, rightTokenType);
  }

  @ApiStatus.Internal
  public static int getNextLineStopOffset(@NotNull Editor editor, @NotNull CaretStop lineStop) {
    final Document document = editor.getDocument();
    final CaretModel caretModel = editor.getCaretModel();

    final int lineNumber = caretModel.getLogicalPosition().line;
    final boolean isAtLineEnd = (caretModel.getOffset() == document.getLineEndOffset(lineNumber));

    return getNextLineStopOffset(document, lineStop, lineNumber, isAtLineEnd);
  }

  private static int getNextLineStopOffset(@NotNull Document document, @NotNull CaretStop lineStop,
                                           int lineNumber, boolean isAtLineEnd) {
    if (lineNumber + 1 >= document.getLineCount()) {
      return document.getTextLength();
    }
    else if (!isAtLineEnd) {
      return lineStop.isAtEnd() ? document.getLineEndOffset(lineNumber) :
             lineStop.isAtStart() ? document.getLineStartOffset(lineNumber + 1) :
             document.getTextLength();
    }
    else {
      return lineStop.isAtStart() ? document.getLineStartOffset(lineNumber + 1) :
             lineStop.isAtEnd() ? document.getLineEndOffset(lineNumber + 1) :
             document.getTextLength();
    }
  }

  @ApiStatus.Internal
  public static int getPreviousLineStopOffset(@NotNull Editor editor, @NotNull CaretStop lineStop) {
    final Document document = editor.getDocument();
    final CaretModel caretModel = editor.getCaretModel();

    final int lineNumber = caretModel.getLogicalPosition().line;
    final boolean isAtLineStart = (caretModel.getOffset() == document.getLineStartOffset(lineNumber));

    return getPreviousLineStopOffset(document, lineStop, lineNumber, isAtLineStart);
  }

  private static int getPreviousLineStopOffset(@NotNull Document document, @NotNull CaretStop lineStop,
                                               int lineNumber, boolean isAtLineStart) {
    if (lineNumber - 1 < 0) {
      return 0;
    }
    else if (!isAtLineStart) {
      return lineStop.isAtStart() ? document.getLineStartOffset(lineNumber) :
             lineStop.isAtEnd() ? document.getLineEndOffset(lineNumber - 1) :
             0;
    }
    else {
      return lineStop.isAtEnd() ? document.getLineEndOffset(lineNumber - 1) :
             lineStop.isAtStart() ? document.getLineStartOffset(lineNumber - 1) :
             0;
    }
  }

  public static boolean isWordOrLexemeStart(@NotNull Editor editor, int offset, boolean isCamel) {
    CharSequence chars = editor.getDocument().getCharsSequence();
    return isWordStart(chars, offset, isCamel) || !isWordEnd(chars, offset, isCamel) && isLexemeBoundary(editor, offset);
  }

  public static boolean isWordOrLexemeEnd(@NotNull Editor editor, int offset, boolean isCamel) {
    CharSequence chars = editor.getDocument().getCharsSequence();
    return isWordEnd(chars, offset, isCamel) || !isWordStart(chars, offset, isCamel) && isLexemeBoundary(editor, offset);
  }

  /**
   * Finds out whether there's a boundary between two lexemes of different type at given offset.
   */
  public static boolean isLexemeBoundary(@NotNull Editor editor, int offset) {
    if (offset <= 0 || offset >= editor.getDocument().getTextLength() ||
        DocumentUtil.isInsideSurrogatePair(editor.getDocument(), offset) ||
        isBetweenWhitespaces(editor.getDocument().getCharsSequence(), offset)) {
      return false;
    }
    EditorHighlighter highlighter = editor.getHighlighter();
    HighlighterIterator it = highlighter.createIterator(offset);
    return retreatTokenOnBoundary(it, editor.getDocument().getCharsSequence(), offset);
  }

  /**
   * Depending on the current caret position and 'smart Home' editor settings, moves caret to the start of current visual line
   * or to the first non-whitespace character on it.
   *
   * @param isWithSelection if true - sets selection from old caret position to the new one, if false - clears selection
   *
   * @see EditorActionUtil#moveCaretToLineStartIgnoringSoftWraps(Editor)
   */
  public static void moveCaretToLineStart(@NotNull Editor editor, boolean isWithSelection) {
    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = caretModel.getLogicalPosition();
    EditorSettings editorSettings = editor.getSettings();

    int logCaretLine = caretModel.getLogicalPosition().line;
    VisualPosition currentVisCaret = caretModel.getVisualPosition();
    VisualPosition caretLogLineStartVis = editor.offsetToVisualPosition(document.getLineStartOffset(logCaretLine));

    if (currentVisCaret.line > caretLogLineStartVis.line) {
      // Caret is located not at the first visual line of soft-wrapped logical line.
      if (editorSettings.isSmartHome()) {
        moveCaretToStartOfSoftWrappedLine(editor, currentVisCaret);
      }
      else {
        caretModel.moveToVisualPosition(new VisualPosition(currentVisCaret.line, 0));
      }
      setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
      EditorModificationUtil.scrollToCaret(editor);
      return;
    }

    // Skip folded lines.
    int logLineToUse = logCaretLine - 1;
    while (logLineToUse >= 0 && editor.offsetToVisualPosition(document.getLineEndOffset(logLineToUse)).line == currentVisCaret.line) {
      logLineToUse--;
    }
    logLineToUse++;

    if (logLineToUse >= document.getLineCount() || !editorSettings.isSmartHome()) {
      editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(logLineToUse, 0));
    }
    else if (logLineToUse == logCaretLine) {
      int line = currentVisCaret.line;
      int column;
      if (currentVisCaret.column == 0) {
        column = findSmartIndentColumn(editor, currentVisCaret.line);
      }
      else {
        column = findFirstNonSpaceColumnOnTheLine(editor, currentVisCaret.line);
        if (column >= currentVisCaret.column) {
          column = 0;
        }
      }
      caretModel.moveToVisualPosition(new VisualPosition(line, Math.max(column, 0)));
    }
    else {
      LogicalPosition logLineEndLog = editor.offsetToLogicalPosition(document.getLineEndOffset(logLineToUse));
      VisualPosition logLineEndVis = editor.logicalToVisualPosition(logLineEndLog);
      int softWrapCount = EditorUtil.getSoftWrapCountAfterLineStart(editor, logLineEndLog);
      if (softWrapCount > 0) {
        moveCaretToStartOfSoftWrappedLine(editor, logLineEndVis);
      }
      else {
        int line = logLineEndVis.line;
        int column = 0;
        if (currentVisCaret.column > 0) {
          int firstNonSpaceColumnOnTheLine = Math.max(0, findFirstNonSpaceColumnOnTheLine(editor, currentVisCaret.line));
          if (firstNonSpaceColumnOnTheLine < currentVisCaret.column) {
            column = firstNonSpaceColumnOnTheLine;
          }
        }
        caretModel.moveToVisualPosition(new VisualPosition(line, column));
      }
    }

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
    EditorModificationUtilEx.scrollToCaret(editor);
  }

  private static void moveCaretToStartOfSoftWrappedLine(@NotNull Editor editor, VisualPosition currentVisual) {
    CaretModel caretModel = editor.getCaretModel();
    int startLineOffset = editor.visualPositionToOffset(new VisualPosition(currentVisual.line, 0));
    SoftWrapModel softWrapModel = editor.getSoftWrapModel();
    SoftWrap softWrap = softWrapModel.getSoftWrap(startLineOffset);
    if (softWrap == null) {
      // Don't expect to be here.
      int column = findFirstNonSpaceColumnOnTheLine(editor, currentVisual.line);
      int columnToMove = column;
      if (column < 0 || currentVisual.column <= column && currentVisual.column > 0) {
        columnToMove = 0;
      }
      caretModel.moveToVisualPosition(new VisualPosition(currentVisual.line, columnToMove));
      return;
    }

    if (currentVisual.column > softWrap.getIndentInColumns()) {
      caretModel.moveToOffset(softWrap.getStart());
    }
    else if (currentVisual.column > 0) {
      caretModel.moveToVisualPosition(new VisualPosition(currentVisual.line, 0));
    }
    else {
      // We assume that caret is already located at zero visual column of soft-wrapped line if control flow reaches this place.
      int lineStartOffset = EditorUtil.getNotFoldedLineStartOffset(editor, startLineOffset);
      int visualLine = editor.offsetToVisualPosition(lineStartOffset).line;
      caretModel.moveToVisualPosition(new VisualPosition(visualLine, Math.max(0, findFirstNonSpaceColumnOnTheLine(editor, visualLine))));
    }
  }

  private static int findSmartIndentColumn(@NotNull Editor editor, int visualLine) {
    for (int i = visualLine; i >= 0; i--) {
      int column = findFirstNonSpaceColumnOnTheLine(editor, i);
      if (column >= 0) {
        return column;
      }
    }
    return 0;
  }

  /**
   * Returns the visual column that points to the first non-whitespace symbol at the visual line in the given editor,
   * or -1 if there is no such column.
   */
  public static int findFirstNonSpaceColumnOnTheLine(@NotNull Editor editor, int visualLineNumber) {
    int startOffset = editor.visualPositionToOffset(new VisualPosition(visualLineNumber, 0));
    int endOffset = EditorUtil.getNotFoldedLineEndOffset(editor, startOffset);
    int offset = findFirstNonSpaceOffsetInRange(editor.getDocument().getImmutableCharSequence(), startOffset, endOffset);
    if (offset == -1) return -1;
    VisualPosition targetPosition = editor.offsetToVisualPosition(offset, true, false);
    return targetPosition.line == visualLineNumber ? targetPosition.column : -1;
  }

  public static int findFirstNonSpaceOffsetOnTheLine(@NotNull Document document, int lineNumber) {
    int lineStart = document.getLineStartOffset(lineNumber);
    int lineEnd = document.getLineEndOffset(lineNumber);
    int result = findFirstNonSpaceOffsetInRange(document.getCharsSequence(), lineStart, lineEnd);
    return result >= 0 ? result : lineEnd;
  }

  /**
   * Tries to find non white space symbol at the given range at the given document.
   *
   * @param text        text to be inspected
   * @param start       target start offset (inclusive)
   * @param end         target end offset (exclusive)
   * @return            index of the first non-white space character at the given document at the given range if the one is found;
   *                    {@code '-1'} otherwise
   */
  public static int findFirstNonSpaceOffsetInRange(@NotNull CharSequence text, int start, int end) {
    for (; start < end; start++) {
      char c = text.charAt(start);
      if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
        return start;
      }
    }
    return -1;
  }

  public static void moveCaretToLineEnd(@NotNull Editor editor, boolean isWithSelection) {
    moveCaretToLineEnd(editor, isWithSelection, true);
  }

  /**
   * Moves caret to visual line end.
   *
   * @param editor target editor
   * @param isWithSelection whether selection should be set from original caret position to its target position
   * @param ignoreTrailingWhitespace if {@code true}, line end will be determined while ignoring trailing whitespace, unless caret is
   *                                 already at so-determined target position, in which case trailing whitespace will be taken into account
   */
  public static void moveCaretToLineEnd(@NotNull Editor editor, boolean isWithSelection, boolean ignoreTrailingWhitespace) {
    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = caretModel.getLogicalPosition();
    SoftWrapModel softWrapModel = editor.getSoftWrapModel();

    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    if (lineNumber >= document.getLineCount()) {
      LogicalPosition pos = new LogicalPosition(lineNumber, 0);
      editor.getCaretModel().moveToLogicalPosition(pos);
      setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
      EditorModificationUtilEx.scrollToCaret(editor);
      return;
    }
    VisualPosition currentVisualCaret = editor.getCaretModel().getVisualPosition();
    VisualPosition visualEndOfLineWithCaret
      = new VisualPosition(currentVisualCaret.line, EditorUtil.getLastVisualLineColumnNumber(editor, currentVisualCaret.line), true);

    // There is a possible case that the caret is already located at the visual end of line and the line is soft wrapped.
    // We want to move the caret to the end of the logical line then.
    if (currentVisualCaret.equals(visualEndOfLineWithCaret)) {
      int offset = editor.visualPositionToOffset(visualEndOfLineWithCaret);
      if (offset < editor.getDocument().getTextLength()) {
        int logicalLineEndOffset = EditorUtil.getNotFoldedLineEndOffset(editor, offset);
        visualEndOfLineWithCaret = editor.offsetToVisualPosition(logicalLineEndOffset, true, false);
      }
    }

    LogicalPosition logLineEnd = editor.visualToLogicalPosition(visualEndOfLineWithCaret);
    int offset = editor.logicalPositionToOffset(logLineEnd);
    lineNumber = logLineEnd.line;
    int newOffset = offset;

    CharSequence text = document.getCharsSequence();
    for (int i = newOffset - 1; i >= document.getLineStartOffset(lineNumber); i--) {
      if (softWrapModel.getSoftWrap(i) != null) {
        newOffset = offset;
        break;
      }
      if (text.charAt(i) != ' ' && text.charAt(i) != '\t') {
        break;
      }
      newOffset = i;
    }

    // Move to the calculated end of visual line if caret is located on a last non-white space symbols on a line and there are
    // remaining white space symbols.
    if (newOffset == offset || newOffset == caretModel.getOffset() || !ignoreTrailingWhitespace) {
      caretModel.moveToVisualPosition(visualEndOfLineWithCaret);
    }
    else {
      if (editor instanceof EditorImpl) {
        caretModel.moveToLogicalPosition(editor.offsetToLogicalPosition(newOffset).leanForward(true));
      }
      else {
        caretModel.moveToOffset(newOffset);
      }
    }

    EditorModificationUtilEx.scrollToCaret(editor);

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  public static void moveCaretToNextWord(@NotNull Editor editor, boolean isWithSelection, boolean camel) {
    moveToNextCaretStop(editor, EditorSettingsExternalizable.getInstance().getCaretStopOptions().getForwardPolicy(),
                        isWithSelection, camel);
  }

  @ApiStatus.Internal
  public static void moveToNextCaretStop(@NotNull Editor editor, @NotNull CaretStopPolicy caretStopPolicy,
                                         boolean isWithSelection, boolean isCamel) {
    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = caretModel.getLogicalPosition();

    int offset = caretModel.getOffset();
    if (offset == document.getTextLength()) {
      return;
    }

    int newOffset;

    FoldRegion currentFoldRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(offset);
    if (currentFoldRegion != null) {
      newOffset = currentFoldRegion.getEndOffset();
    }
    else {
      newOffset = getNextCaretStopOffset(editor, caretStopPolicy, isCamel);
      if (newOffset == offset) return;

      FoldRegion foldRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(newOffset);
      if (foldRegion != null) {
        newOffset = foldRegion.getStartOffset();
      }
    }
    if (editor instanceof EditorImpl) {
      int boundaryOffset = ((EditorImpl)editor).findNearestDirectionBoundary(offset, true);
      if (boundaryOffset >= 0) {
        newOffset = Math.min(boundaryOffset, newOffset);
      }
    }
    caretModel.moveToOffset(newOffset);
    EditorModificationUtilEx.scrollToCaret(editor);

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  private static void setupSelection(@NotNull Editor editor,
                                     boolean isWithSelection,
                                     int selectionStart,
                                     @NotNull LogicalPosition blockSelectionStart) {
    SelectionModel selectionModel = editor.getSelectionModel();
    CaretModel caretModel = editor.getCaretModel();
    if (isWithSelection) {
      if (editor.isColumnMode() && !caretModel.supportsMultipleCarets()) {
        selectionModel.setBlockSelection(blockSelectionStart, caretModel.getLogicalPosition());
      }
      else {
        selectionModel.setSelection(selectionStart, caretModel.getVisualPosition(), caretModel.getOffset());
      }
    }
    else {
      selectionModel.removeSelection();
    }

    selectNonexpandableFold(editor);
  }

  private static final Key<VisualPosition> PREV_POS = Key.create("PREV_POS");
  public static void selectNonexpandableFold(@NotNull Editor editor) {
    final CaretModel caretModel = editor.getCaretModel();
    final VisualPosition pos = caretModel.getVisualPosition();

    VisualPosition prevPos = editor.getUserData(PREV_POS);

    if (prevPos != null) {
      int columnShift = pos.line == prevPos.line ? pos.column - prevPos.column : 0;

      int caret = caretModel.getOffset();
      final FoldRegion collapsedUnderCaret = editor.getFoldingModel().getCollapsedRegionAtOffset(caret);
      if (collapsedUnderCaret != null && collapsedUnderCaret.shouldNeverExpand() &&
          Boolean.TRUE.equals(collapsedUnderCaret.getUserData(FoldingModelImpl.SELECT_REGION_ON_CARET_NEARBY))) {
        if (caret > collapsedUnderCaret.getStartOffset() && columnShift > 0) {
          caretModel.moveToOffset(collapsedUnderCaret.getEndOffset());
        }
        else if (caret + 1 < collapsedUnderCaret.getEndOffset() && columnShift < 0) {
          caretModel.moveToOffset(collapsedUnderCaret.getStartOffset());
        }
        editor.getSelectionModel().setSelection(collapsedUnderCaret.getStartOffset(), collapsedUnderCaret.getEndOffset());
      }
    }

    editor.putUserData(PREV_POS, pos);
  }

  public static void moveCaretToPreviousWord(@NotNull Editor editor, boolean isWithSelection, boolean camel) {
    moveToPreviousCaretStop(editor, EditorSettingsExternalizable.getInstance().getCaretStopOptions().getBackwardPolicy(),
                            isWithSelection, camel);
  }

  @ApiStatus.Internal
  public static void moveToPreviousCaretStop(@NotNull Editor editor, @NotNull CaretStopPolicy caretStopPolicy,
                                             boolean isWithSelection, boolean isCamel) {
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = caretModel.getLogicalPosition();

    int offset = editor.getCaretModel().getOffset();
    if (offset == 0) return;

    int newOffset;

    FoldRegion currentFoldRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(offset - 1);
    if (currentFoldRegion != null) {
      newOffset = currentFoldRegion.getStartOffset();
    }
    else {
      newOffset = getPreviousCaretStopOffset(editor, caretStopPolicy, isCamel);
      if (newOffset == offset) return;

      FoldRegion foldRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(newOffset);
      if (foldRegion != null && newOffset > foldRegion.getStartOffset()) {
        newOffset = foldRegion.getEndOffset();
      }
    }

    if (editor instanceof EditorImpl) {
      int boundaryOffset = ((EditorImpl)editor).findNearestDirectionBoundary(offset, false);
      if (boundaryOffset >= 0) {
        newOffset = Math.max(boundaryOffset, newOffset);
      }
      caretModel.moveToLogicalPosition(editor.offsetToLogicalPosition(newOffset).leanForward(true));
    }
    else {
      editor.getCaretModel().moveToOffset(newOffset);
    }
    EditorModificationUtilEx.scrollToCaret(editor);

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  public static void moveCaretPageUp(@NotNull Editor editor, boolean isWithSelection) {
    int lineHeight = editor.getLineHeight();
    Rectangle visibleArea = getVisibleArea(editor);
    editor.getScrollingModel().scrollVertically(adjustYToVisualLineBase(editor,
                                                                         visibleArea.y - visibleArea.height / lineHeight * lineHeight));
    int lineShift = calcVisualLineIncrement(editor, editor.getCaretModel().getVisualPosition().line, -visibleArea.height);
    editor.getCaretModel().moveCaretRelatively(0, lineShift, isWithSelection, editor.isColumnMode(), true);
  }

  public static void moveCaretPageDown(@NotNull Editor editor, boolean isWithSelection) {
    int lineHeight = editor.getLineHeight();
    Rectangle visibleArea = getVisibleArea(editor);
    int allowedBottom = ((EditorEx)editor).getContentSize().height - visibleArea.height;
    editor.getScrollingModel().scrollVertically(
      Math.min(allowedBottom, adjustYToVisualLineBase(editor, visibleArea.y + visibleArea.height / lineHeight * lineHeight)));
    int lineShift = calcVisualLineIncrement(editor, editor.getCaretModel().getVisualPosition().line, visibleArea.height);
    editor.getCaretModel().moveCaretRelatively(0, lineShift, isWithSelection, editor.isColumnMode(), true);
  }

  public static void moveCaretToTextStart(@NotNull Editor editor, @Nullable Project project) {
    editor.getCaretModel().removeSecondaryCarets();
    editor.getCaretModel().moveToOffset(0);
    editor.getSelectionModel().removeSelection();

    ScrollingModel scrollingModel = editor.getScrollingModel();
    scrollingModel.disableAnimation();
    scrollingModel.scrollToCaret(ScrollType.RELATIVE);
    scrollingModel.enableAnimation();

    if (project != null) {
      IdeDocumentHistory instance = IdeDocumentHistory.getInstance(project);
      if (instance != null) {
        instance.includeCurrentCommandAsNavigation();
      }
    }
  }

  public static void moveCaretToTextEnd(@NotNull Editor editor, @Nullable Project project) {
    editor.getCaretModel().removeSecondaryCarets();
    int offset = editor.getDocument().getTextLength();
    if (editor instanceof EditorImpl) {
      editor.getCaretModel().moveToLogicalPosition(editor.offsetToLogicalPosition(offset).leanForward(true));
    }
    else {
      editor.getCaretModel().moveToOffset(offset);
    }
    editor.getSelectionModel().removeSelection();

    ScrollingModel scrollingModel = editor.getScrollingModel();
    scrollingModel.disableAnimation();
    scrollingModel.scrollToCaret(ScrollType.CENTER);
    scrollingModel.enableAnimation();

    if (project != null) {
      IdeDocumentHistory instance = IdeDocumentHistory.getInstance(project);
      if (instance != null) {
        instance.includeCurrentCommandAsNavigation();
      }
    }
  }


  private static int adjustYToVisualLineBase(@NotNull Editor editor, int y) {
    int visualLineBaseY = editor.visualLineToY(editor.yToVisualLine(y));
    return y > visualLineBaseY && y < visualLineBaseY + editor.getLineHeight() ? visualLineBaseY : y;
  }

  private static int calcVisualLineIncrement(@NotNull Editor editor, int visualLine, int yIncrement) {
    int startY = editor.visualLineToY(visualLine) + (yIncrement > 0 ? editor.getLineHeight() - 1 : 0);
    int targetY = startY + yIncrement;
    int targetVisualLine = editor.yToVisualLine(targetY);
    int targetVisualLineBase = editor.visualLineToY(targetVisualLine);
    if (targetY < targetVisualLineBase) {
      if (yIncrement < 0) targetVisualLine--;
    }
    else if (targetY >= targetVisualLineBase + editor.getLineHeight()) {
      if (yIncrement > 0) targetVisualLine++;
    }
    return targetVisualLine - visualLine;
  }

  public static void moveCaretPageTop(@NotNull Editor editor, boolean isWithSelection) {
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = caretModel.getLogicalPosition();
    Rectangle visibleArea = getVisibleArea(editor);
    int lineNumber = editor.yToVisualLine(visibleArea.y);
    if (visibleArea.y > editor.visualLineToY(lineNumber) && visibleArea.y + visibleArea.height > editor.visualLineToY(lineNumber + 1)) {
      lineNumber++;
    }
    VisualPosition pos = new VisualPosition(lineNumber, editor.getCaretModel().getVisualPosition().column);
    editor.getCaretModel().moveToVisualPosition(pos);
    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  public static void moveCaretPageBottom(@NotNull Editor editor, boolean isWithSelection) {
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = caretModel.getLogicalPosition();
    Rectangle visibleArea = getVisibleArea(editor);
    int maxY = visibleArea.y + visibleArea.height - editor.getLineHeight();
    int lineNumber = editor.yToVisualLine(maxY);
    if (lineNumber > 0 && maxY < editor.visualLineToY(lineNumber) && visibleArea.y <= editor.visualLineToY(lineNumber - 1)) {
      lineNumber--;
    }
    VisualPosition pos = new VisualPosition(lineNumber, editor.getCaretModel().getVisualPosition().column);
    editor.getCaretModel().moveToVisualPosition(pos);
    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  private static @NotNull Rectangle getVisibleArea(@NotNull Editor editor) {
    ScrollingModel model = editor.getScrollingModel();
    return EditorCoreUtil.isTrueSmoothScrollingEnabled() ? model.getVisibleAreaOnScrollingFinished() : model.getVisibleArea();
  }

  private static boolean isBetweenWhitespaces(@NotNull CharSequence text, int offset) {
    return 0 < offset && offset < text.length() &&
           isWhitespace(text.charAt(offset - 1)) &&
           isWhitespace(text.charAt(offset));
  }

  public static boolean isWordStart(@NotNull CharSequence text, int offset, boolean isCamel) {
    return isWordBoundary(text, offset, isCamel, true);
  }

  public static boolean isWordEnd(@NotNull CharSequence text, int offset, boolean isCamel) {
    return isWordBoundary(text, offset, isCamel, false);
  }

  public static boolean isWordBoundary(@NotNull CharSequence text, int offset, boolean isCamel, boolean isStart) {
    if (offset < 0 || offset > text.length()) return false;

    final char prev = offset > 0 ? text.charAt(offset - 1) : 0;
    final char curr = offset < text.length() ? text.charAt(offset) : 0;

    final char word = isStart ? curr : prev;
    final char neighbor = isStart ? prev : curr;

    if (isJavaIdentifierPart(word)) {
      if (!isJavaIdentifierPart(neighbor)) return true;
      if (isCamel && isHumpBound(text, offset, isStart)) return true;
    }
    if (isPunctuation(word) && !isPunctuation(neighbor)) return true;

    return false;
  }

  public static boolean isHumpBound(@NotNull CharSequence text, int offset, boolean isStart) {
    if (offset <= 0 || offset >= text.length()) return false;

    final char prev = text.charAt(offset - 1);
    final char curr = text.charAt(offset);
    final char next = offset + 1 < text.length() ? text.charAt(offset + 1) : 0; // 0x00 is not lowercase.

    final char hump = isStart ? curr : prev;
    final char neighbor = isStart ? prev : curr;

    return isLowerCaseOrDigit(prev) && isUpperCase(curr) ||
           neighbor == '_' && hump != '_' ||
           neighbor == '$' && isLetterOrDigit(hump) ||
           isUpperCase(prev) && isUpperCase(curr) && isLowerCase(next);
  }

  private static boolean isLowerCaseOrDigit(char c) {
    return isLowerCase(c) || isDigit(c);
  }

  private static boolean isPunctuation(char c) {
    return !(isJavaIdentifierPart(c) || isWhitespace(c));
  }

  /**
   * This method moves caret to the nearest preceding visual line start, which is not a soft line wrap
   *
   * @see EditorUtil#calcCaretLineRange(Editor)
   * @see EditorActionUtil#moveCaretToLineStart(Editor, boolean)
   */
  public static void moveCaretToLineStartIgnoringSoftWraps(@NotNull Editor editor) {
    editor.getCaretModel().moveToLogicalPosition(EditorUtil.calcCaretLineRange(editor).first);
  }

  /**
   * This method will make required expansions of collapsed region to make given offset 'visible'.
   */
  public static void makePositionVisible(final @NotNull Editor editor, final int offset) {
    FoldingModel foldingModel = editor.getFoldingModel();
    while (true) {
      FoldRegion region = foldingModel.getCollapsedRegionAtOffset(offset);
      if (region == null || region.shouldNeverExpand()) break;
      foldingModel.runBatchFoldingOperation(() -> region.setExpanded(true));
    }
  }

  public static void moveCaret(@NotNull Caret caret, int offset, boolean withSelection) {
    if (withSelection) {
      caret.setSelection(caret.getLeadSelectionOffset(), offset);
    }
    else {
      caret.removeSelection();
    }
    caret.moveToOffset(offset);
    EditorModificationUtilEx.scrollToCaret(caret.getEditor());
  }
}
