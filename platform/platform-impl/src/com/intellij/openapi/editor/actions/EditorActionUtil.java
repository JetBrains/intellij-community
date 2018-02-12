// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.actions;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FoldingModelImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.DocumentUtil;
import com.intellij.util.EditorPopupHandler;
import com.intellij.util.SystemProperties;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

public class EditorActionUtil {
  protected static final Object EDIT_COMMAND_GROUP = Key.create("EditGroup");
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
    EditorSettings editorSettings = editor.getSettings();
    int tabSize = editorSettings.getTabSize(project);
    Document document = editor.getDocument();
    CharSequence text = document.getImmutableCharSequence();
    int spacesEnd = 0;
    int lineStart = 0;
    int lineEnd = 0;
    int tabsEnd = 0;
    if (lineNumber < document.getLineCount()) {
      lineStart = document.getLineStartOffset(lineNumber);
      lineEnd = document.getLineEndOffset(lineNumber);
      spacesEnd = lineStart;
      boolean inTabs = true;
      for (; spacesEnd <= lineEnd; spacesEnd++) {
        if (spacesEnd == lineEnd) {
          break;
        }
        char c = text.charAt(spacesEnd);
        if (c != '\t') {
          if (inTabs) {
            inTabs = false;
            tabsEnd = spacesEnd;
          }
          if (c != ' ') break;
        }
      }
      if (inTabs) {
        tabsEnd = lineEnd;
      } 
    }
    int newCaretOffset = caretOffset;
    if (newCaretOffset >= lineStart && newCaretOffset < lineEnd && spacesEnd == lineEnd) {
      spacesEnd = newCaretOffset;
      tabsEnd = Math.min(spacesEnd, tabsEnd);
    }
    int oldLength = getSpaceWidthInColumns(text, lineStart, spacesEnd, tabSize);
    tabsEnd = getSpaceWidthInColumns(text, lineStart, tabsEnd, tabSize);

    int newLength = oldLength + indent;
    if (newLength < 0) {
      newLength = 0;
    }
    tabsEnd += indent;
    if (tabsEnd < 0) tabsEnd = 0;
    if (!shouldUseSmartTabs(project, editor)) tabsEnd = newLength;
    StringBuilder buf = new StringBuilder(newLength);
    for (int i = 0; i < newLength;) {
      if (tabSize > 0 && editorSettings.isUseTabCharacter(project) && i + tabSize <= tabsEnd) {
        buf.append('\t');
        //noinspection AssignmentToForLoopParameter
        i += tabSize;
      }
      else {
        buf.append(' ');
        //noinspection AssignmentToForLoopParameter
        i++;
      }
    }

    int newSpacesEnd = lineStart + buf.length();
    if (newCaretOffset >= spacesEnd) {
      newCaretOffset += buf.length() - (spacesEnd - lineStart);
    }
    else if (newCaretOffset >= lineStart && newCaretOffset < spacesEnd && newCaretOffset > newSpacesEnd) {
      newCaretOffset = newSpacesEnd;
    }

    if (buf.length() > 0) {
      if (spacesEnd > lineStart) {
        document.replaceString(lineStart, spacesEnd, buf.toString());
      }
      else {
        document.insertString(lineStart, buf.toString());
      }
    }
    else {
      if (spacesEnd > lineStart) {
        document.deleteString(lineStart, spacesEnd);
      }
    }

    return newCaretOffset;
  }

  private static int getSpaceWidthInColumns(CharSequence seq, int startOffset, int endOffset, int tabSize) {
    int result = 0;
    for (int i = startOffset; i < endOffset; i++) {
      if (seq.charAt(i) == '\t') {
        result = (result / tabSize + 1) * tabSize;
      }
      else {
        result++;
      }
    }
    return result;
  }

  private static boolean shouldUseSmartTabs(Project project, @NotNull Editor editor) {
    if (!(editor instanceof EditorEx)) return false;
    return CodeStyle.getIndentOptions(project, editor.getDocument()).SMART_TABS;
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
    if (!(editor instanceof EditorEx) ||
        offset <= 0 || offset >= editor.getDocument().getTextLength() ||
        DocumentUtil.isInsideSurrogatePair(editor.getDocument(), offset)) {
      return false;
    }
    if (CharArrayUtil.isEmptyOrSpaces(editor.getDocument().getImmutableCharSequence(), offset - 1, offset + 1)) return false;
    EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    HighlighterIterator it = highlighter.createIterator(offset);
    if (it.getStart() != offset) {
      return false;
    }
    IElementType rightToken = it.getTokenType();
    it.retreat();
    IElementType leftToken = it.getTokenType();
    return !Comparing.equal(leftToken, rightToken);
  }

  public static boolean isWordStart(@NotNull CharSequence text, int offset, boolean isCamel) {
    char prev = offset > 0 ? text.charAt(offset - 1) : 0;
    char current = text.charAt(offset);

    final boolean firstIsIdentifierPart = Character.isJavaIdentifierPart(prev);
    final boolean secondIsIdentifierPart = Character.isJavaIdentifierPart(current);
    if (!firstIsIdentifierPart && secondIsIdentifierPart) {
      return true;
    }

    if (isCamel && firstIsIdentifierPart && secondIsIdentifierPart && isHumpBound(text, offset, true)) {
      return true;
    }

    return (Character.isWhitespace(prev) || firstIsIdentifierPart) &&
           !Character.isWhitespace(current) && !secondIsIdentifierPart;
  }
  
  private static boolean isLowerCaseOrDigit(char c) {
    return Character.isLowerCase(c) || Character.isDigit(c);
  }

  public static boolean isWordEnd(@NotNull CharSequence text, int offset, boolean isCamel) {
    char prev = offset > 0 ? text.charAt(offset - 1) : 0;
    char current = text.charAt(offset);
    char next = offset + 1 < text.length() ? text.charAt(offset + 1) : 0;

    final boolean firstIsIdentifierPart = Character.isJavaIdentifierPart(prev);
    final boolean secondIsIdentifierPart = Character.isJavaIdentifierPart(current);
    if (firstIsIdentifierPart && !secondIsIdentifierPart) {
      return true;
    }

    if (isCamel) {
      if (firstIsIdentifierPart
          && (Character.isLowerCase(prev) && Character.isUpperCase(current)
              || prev != '_' && current == '_'
              || Character.isUpperCase(prev) && Character.isUpperCase(current) && Character.isLowerCase(next)))
      {
        return true;
      }
    }

    return !Character.isWhitespace(prev) && !firstIsIdentifierPart &&
           (Character.isWhitespace(current) || secondIsIdentifierPart);
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
          int firstNonSpaceColumnOnTheLine = findFirstNonSpaceColumnOnTheLine(editor, currentVisCaret.line);
          if (firstNonSpaceColumnOnTheLine < currentVisCaret.column) {
            column = firstNonSpaceColumnOnTheLine;
          }
        }
        caretModel.moveToVisualPosition(new VisualPosition(line, column));
      }
    }

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
    EditorModificationUtil.scrollToCaret(editor);
  }

  private static void moveCaretToStartOfSoftWrappedLine(@NotNull Editor editor, VisualPosition currentVisual) {
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition startLineLogical = editor.visualToLogicalPosition(new VisualPosition(currentVisual.line, 0));
    int startLineOffset = editor.logicalPositionToOffset(startLineLogical);
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
      caretModel.moveToVisualPosition(new VisualPosition(visualLine, findFirstNonSpaceColumnOnTheLine(editor, visualLine)));
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
   * Tries to find visual column that points to the first non-white space symbol at the visual line at the given editor.
   *
   * @param editor              target editor
   * @param visualLineNumber    target visual line
   * @return                    visual column that points to the first non-white space symbol at the target visual line if the one exists;
   *                            {@code '-1'} otherwise
   */
  public static int findFirstNonSpaceColumnOnTheLine(@NotNull Editor editor, int visualLineNumber) {
    Document document = editor.getDocument();
    VisualPosition visLine = new VisualPosition(visualLineNumber, 0);
    int logLine = editor.visualToLogicalPosition(visLine).line;
    int logLineStartOffset = document.getLineStartOffset(logLine);
    int logLineEndOffset = document.getLineEndOffset(logLine);
    LogicalPosition logLineStart = editor.offsetToLogicalPosition(logLineStartOffset);
    VisualPosition visLineStart = editor.logicalToVisualPosition(logLineStart);
    boolean newRendering = editor instanceof EditorImpl;

    boolean softWrapIntroducedLine = visLineStart.line != visualLineNumber;
    if (!softWrapIntroducedLine) {
      int offset = findFirstNonSpaceOffsetInRange(document.getCharsSequence(), logLineStartOffset, logLineEndOffset);
      if (offset >= 0) {
        return newRendering ? editor.offsetToVisualPosition(offset).column : 
               EditorUtil.calcColumnNumber(editor, document.getCharsSequence(), logLineStartOffset, offset);
      }
      else {
        return -1;
      }
    }

    int lineFeedsToSkip = visualLineNumber - visLineStart.line;
    List<? extends SoftWrap> softWraps = editor.getSoftWrapModel().getSoftWrapsForLine(logLine);
    for (SoftWrap softWrap : softWraps) {
      CharSequence softWrapText = softWrap.getText();
      int softWrapLineFeedsNumber = StringUtil.countNewLines(softWrapText);

      if (softWrapLineFeedsNumber < lineFeedsToSkip) {
        lineFeedsToSkip -= softWrapLineFeedsNumber;
        continue;
      }

      // Point to the first non-white space symbol at the target soft wrap visual line or to the first non-white space symbol
      // of document line that follows it if possible.
      int softWrapTextLength = softWrapText.length();
      boolean skip = true;
      for (int j = 0; j < softWrapTextLength; j++) {
        if (softWrapText.charAt(j) == '\n') {
          skip = --lineFeedsToSkip > 0;
          continue;
        }
        if (skip) {
          continue;
        }

        int nextSoftWrapLineFeedOffset = StringUtil.indexOf(softWrapText, '\n', j, softWrapTextLength);

        int end = findFirstNonSpaceOffsetInRange(softWrapText, j, softWrapTextLength);
        if (end >= 0) {
          assert !newRendering : "Unexpected soft wrap text";
          // Non space symbol is contained at soft wrap text after offset that corresponds to the target visual line start.
          if (nextSoftWrapLineFeedOffset < 0 || end < nextSoftWrapLineFeedOffset) {
            return EditorUtil.calcColumnNumber(editor, softWrapText, j, end);
          }
          else {
            return -1;
          }
        }

        if (nextSoftWrapLineFeedOffset >= 0) {
          // There are soft wrap-introduced visual lines after the target one
          return -1;
        }
      }
      int end = findFirstNonSpaceOffsetInRange(document.getCharsSequence(), softWrap.getStart(), logLineEndOffset);
      if (end >= 0) {
        return newRendering ? editor.offsetToVisualPosition(end).column : 
               EditorUtil.calcColumnNumber(editor, document.getCharsSequence(), softWrap.getStart(), end);
      }
      else {
        return -1;
      }
    }
    return -1;
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
      if (c != ' ' && c != '\t') {
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
      EditorModificationUtil.scrollToCaret(editor);
      return;
    }
    VisualPosition currentVisualCaret = editor.getCaretModel().getVisualPosition();
    VisualPosition visualEndOfLineWithCaret
      = new VisualPosition(currentVisualCaret.line, EditorUtil.getLastVisualLineColumnNumber(editor, currentVisualCaret.line), true);

    // There is a possible case that the caret is already located at the visual end of line and the line is soft wrapped.
    // We want to move the caret to the end of the logical line then.
    if (currentVisualCaret.equals(visualEndOfLineWithCaret)) {
      LogicalPosition logical = editor.visualToLogicalPosition(visualEndOfLineWithCaret);
      int offset = editor.logicalPositionToOffset(logical);
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

    EditorModificationUtil.scrollToCaret(editor);

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  public static void moveCaretToNextWord(@NotNull Editor editor, boolean isWithSelection, boolean camel) {
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
      newOffset = offset + 1;
      int lineNumber = caretModel.getLogicalPosition().line;
      if (lineNumber >= document.getLineCount()) return;
      int maxOffset = document.getLineEndOffset(lineNumber);
      if (newOffset > maxOffset) {
        if (lineNumber + 1 >= document.getLineCount()) {
          return;
        }
        maxOffset = document.getLineEndOffset(lineNumber + 1);
      }
      for (; newOffset < maxOffset; newOffset++) {
        if (isWordOrLexemeStart(editor, newOffset, camel)) {
          break;
        }
      }
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
    EditorModificationUtil.scrollToCaret(editor);

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
    Document document = editor.getDocument();
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
      int lineNumber = editor.getCaretModel().getLogicalPosition().line;
      newOffset = offset - 1;
      int minOffset = lineNumber > 0 ? document.getLineEndOffset(lineNumber - 1) : 0;
      for (; newOffset > minOffset; newOffset--) {
        if (isWordOrLexemeStart(editor, newOffset, camel)) break;
      }
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
    EditorModificationUtil.scrollToCaret(editor);

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  public static void moveCaretPageUp(@NotNull Editor editor, boolean isWithSelection) {
    int lineHeight = editor.getLineHeight();
    Rectangle visibleArea = getVisibleArea(editor);
    int linesIncrement = visibleArea.height / lineHeight;
    editor.getScrollingModel().scrollVertically(visibleArea.y - visibleArea.y % lineHeight - linesIncrement * lineHeight);
    int lineShift = -linesIncrement;
    editor.getCaretModel().moveCaretRelatively(0, lineShift, isWithSelection, editor.isColumnMode(), true);
  }

  public static void moveCaretPageDown(@NotNull Editor editor, boolean isWithSelection) {
    int lineHeight = editor.getLineHeight();
    Rectangle visibleArea = getVisibleArea(editor);
    int linesIncrement = visibleArea.height / lineHeight;
    int allowedBottom = ((EditorEx)editor).getContentSize().height - visibleArea.height;
    editor.getScrollingModel().scrollVertically(
      Math.min(allowedBottom, visibleArea.y - visibleArea.y % lineHeight + linesIncrement * lineHeight));
    editor.getCaretModel().moveCaretRelatively(0, linesIncrement, isWithSelection, editor.isColumnMode(), true);
  }

  public static void moveCaretPageTop(@NotNull Editor editor, boolean isWithSelection) {
    int lineHeight = editor.getLineHeight();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = caretModel.getLogicalPosition();
    Rectangle visibleArea = getVisibleArea(editor);
    int lineNumber = visibleArea.y / lineHeight;
    if (visibleArea.y % lineHeight > 0) {
      lineNumber++;
    }
    VisualPosition pos = new VisualPosition(lineNumber, editor.getCaretModel().getVisualPosition().column);
    editor.getCaretModel().moveToVisualPosition(pos);
    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  public static void moveCaretPageBottom(@NotNull Editor editor, boolean isWithSelection) {
    int lineHeight = editor.getLineHeight();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = caretModel.getLogicalPosition();
    Rectangle visibleArea = getVisibleArea(editor);
    int lineNumber = Math.max(0, (visibleArea.y + visibleArea.height) / lineHeight - 1);
    VisualPosition pos = new VisualPosition(lineNumber, editor.getCaretModel().getVisualPosition().column);
    editor.getCaretModel().moveToVisualPosition(pos);
    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  @NotNull
  private static Rectangle getVisibleArea(@NotNull Editor editor) {
    return SystemProperties.isTrueSmoothScrollingEnabled() ? editor.getScrollingModel().getVisibleAreaOnScrollingFinished()
                                                           : editor.getScrollingModel().getVisibleArea();
  }

  public static EditorPopupHandler createEditorPopupHandler(@NotNull final String groupId) {
    return new EditorPopupHandler() {
      @Override
      public void invokePopup(final EditorMouseEvent event) {
        if (!event.isConsumed() && event.getArea() == EditorMouseEventArea.EDITING_AREA) {
          ActionGroup group = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(groupId);
          showEditorPopup(event, group);
        }
      }
    };
  }

  public static EditorPopupHandler createEditorPopupHandler(@NotNull final ActionGroup group) {
    return new EditorPopupHandler() {
      @Override
      public void invokePopup(final EditorMouseEvent event) {
        showEditorPopup(event, group);
      }
    };
  }

  private static void showEditorPopup(final EditorMouseEvent event, @NotNull final ActionGroup group) {
    if (!event.isConsumed() && event.getArea() == EditorMouseEventArea.EDITING_AREA) {
      ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_POPUP, group);
      MouseEvent e = event.getMouseEvent();
      final Component c = e.getComponent();
      if (c != null && c.isShowing()) {
        popupMenu.getComponent().show(c, e.getX(), e.getY());
      }
      e.consume();
    }
  }

  public static boolean isHumpBound(@NotNull CharSequence editorText, int offset, boolean start) {
    if (offset <= 0 || offset >= editorText.length()) return false;
    final char prevChar = editorText.charAt(offset - 1);
    final char curChar = editorText.charAt(offset);
    final char nextChar = offset + 1 < editorText.length() ? editorText.charAt(offset + 1) : 0; // 0x00 is not lowercase.

    return isLowerCaseOrDigit(prevChar) && Character.isUpperCase(curChar) ||
        start && prevChar == '_' && curChar != '_' ||
        !start && prevChar != '_' && curChar == '_' ||
        start && prevChar == '$' && Character.isLetterOrDigit(curChar) ||
        !start && Character.isLetterOrDigit(prevChar) && curChar == '$' ||
        Character.isUpperCase(prevChar) && Character.isUpperCase(curChar) && Character.isLowerCase(nextChar);
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
  public static void makePositionVisible(@NotNull final Editor editor, final int offset) {
    FoldingModel foldingModel = editor.getFoldingModel();
    FoldRegion collapsedRegionAtOffset;
    while ((collapsedRegionAtOffset  = foldingModel.getCollapsedRegionAtOffset(offset)) != null) {
      final FoldRegion region = collapsedRegionAtOffset;
      foldingModel.runBatchFoldingOperation(() -> region.setExpanded(true));
    }
  }
}
