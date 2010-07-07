/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 6, 2002
 * Time: 4:54:58 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EditorPopupHandler;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

public class EditorActionUtil {
  protected static final Object EDIT_COMMAND_GROUP = Key.create("EditGroup");
  protected static final Object DELETE_COMMAND_GROUP = Key.create("DeleteGroup");

  private EditorActionUtil() {
  }

  public static void scrollRelatively(Editor editor, int lineShift) {
    if (lineShift != 0) {
      editor.getScrollingModel().scrollVertically(
        editor.getScrollingModel().getVerticalScrollOffset() + lineShift * editor.getLineHeight()
      );
    }

    Rectangle viewRectangle = editor.getScrollingModel().getVisibleArea();
    int lineNumber = editor.getCaretModel().getVisualPosition().line;
    if (viewRectangle != null) {
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
  }

  public static void moveCaretRelativelyAndScroll(Editor editor,
                                                  int columnShift,
                                                  int lineShift,
                                                  boolean withSelection) {
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    VisualPosition pos = editor.getCaretModel().getVisualPosition();
    Point caretLocation = editor.visualPositionToXY(pos);
    int caretVShift = caretLocation.y - visibleArea.y;

    editor.getCaretModel().moveCaretRelatively(columnShift, lineShift, withSelection, false, false);

    //editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    VisualPosition caretPos = editor.getCaretModel().getVisualPosition();
    Point caretLocation2 = editor.visualPositionToXY(caretPos);
    editor.getScrollingModel().scrollVertically(caretLocation2.y - caretVShift);
  }

  @SuppressWarnings({"AssignmentToForLoopParameter"})
  public static void indentLine(Project project, Editor editor, int lineNumber, int indent) {
    EditorSettings editorSettings = editor.getSettings();
    Document document = editor.getDocument();
    int spacesEnd = 0;
    int lineStart = 0;
    if (lineNumber < document.getLineCount()) {
      lineStart = document.getLineStartOffset(lineNumber);
      int lineEnd = document.getLineEndOffset(lineNumber);
      spacesEnd = lineStart;
      CharSequence text = document.getCharsSequence();
      for (; spacesEnd <= lineEnd; spacesEnd++) {
        if (spacesEnd == lineEnd) {
          break;
        }
        char c = text.charAt(spacesEnd);
        if (c != '\t' && c != ' ') {
          break;
        }
      }
    }
    int oldLength = editor.offsetToLogicalPosition(spacesEnd).column;

    int newLength = oldLength + indent;
    if (newLength < 0) {
      newLength = 0;
    }
    StringBuffer buf = new StringBuffer(newLength);
    int tabSize = editorSettings.getTabSize(project);
    for (int i = 0; i < newLength;) {
      if (tabSize > 0 && editorSettings.isUseTabCharacter(project) && i + tabSize <= newLength) {
        buf.append('\t');
        i += tabSize;
      }
      else {
        buf.append(' ');
        i++;
      }
    }

    int newCaretOffset = editor.getCaretModel().getOffset();
    if (newCaretOffset >= spacesEnd) {
      newCaretOffset += buf.length() - (spacesEnd - lineStart);
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

    editor.getCaretModel().moveToOffset(newCaretOffset);
  }

  public static boolean isWordStart(CharSequence text, int offset, boolean isCamel) {
    char prev = offset > 0 ? text.charAt(offset - 1) : 0;
    char current = text.charAt(offset);
    char next = offset + 1 < text.length() ? text.charAt(offset + 1) : 0;

    final boolean firstIsIdentifierPart = Character.isJavaIdentifierPart(prev);
    final boolean secondIsIdentifierPart = Character.isJavaIdentifierPart(current);
    if (!firstIsIdentifierPart && secondIsIdentifierPart) {
      return true;
    }

    if (isCamel) {
      if (firstIsIdentifierPart && secondIsIdentifierPart &&
          (Character.isLowerCase(prev) && Character.isUpperCase(current) ||
           prev == '_' && current != '_' ||
           Character.isUpperCase(prev) && Character.isUpperCase(current) && Character.isLowerCase(next))) {
        return true;
      }
    }

    return (Character.isWhitespace(prev) || firstIsIdentifierPart) &&
           !Character.isWhitespace(current) && !secondIsIdentifierPart;
  }

  public static boolean isWordEnd(CharSequence text, int offset, boolean isCamel) {
    char prev = offset > 0 ? text.charAt(offset - 1) : 0;
    char current = text.charAt(offset);
    char next = offset + 1 < text.length() ? text.charAt(offset + 1) : 0;

    final boolean firstIsIdentifierPart = Character.isJavaIdentifierPart(prev);
    final boolean secondIsIdentifierPart = Character.isJavaIdentifierPart(current);
    if (firstIsIdentifierPart && !secondIsIdentifierPart) {
      return true;
    }

    if (isCamel) {
      if (firstIsIdentifierPart && secondIsIdentifierPart &&
          (Character.isLowerCase(prev) && Character.isUpperCase(current) || prev != '_' && current == '_' ||
          Character.isUpperCase(prev) && Character.isUpperCase(current) && Character.isLowerCase(next))) {
        return true;
      }
    }

    return !Character.isWhitespace(prev) && !firstIsIdentifierPart &&
           (Character.isWhitespace(current) || secondIsIdentifierPart);
  }

  public static void moveCaretToLineStart(Editor editor, boolean isWithSelection) {
    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = selectionModel.hasBlockSelection()
                                          ? selectionModel.getBlockStart()
                                          : caretModel.getLogicalPosition();
    EditorSettings editorSettings = editor.getSettings();

    int logCaretLine = caretModel.getLogicalPosition().line;
    VisualPosition currentVisCaret = caretModel.getVisualPosition();
    VisualPosition caretLogLineStartVis = editor.offsetToVisualPosition(document.getLineStartOffset(logCaretLine));

    if (currentVisCaret.line > caretLogLineStartVis.line) {
      // Caret is located not at the first visual line of soft-wrapped logical line.
      moveCaretToStartOfSoftWrappedLine(editor, currentVisCaret);
      setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      return;
    }

    // Skip folded lines.
    int logLineToUse = logCaretLine - 1;
    while (logLineToUse >= 0 && editor.offsetToVisualPosition(document.getLineEndOffset(logLineToUse)).line == currentVisCaret.line) {
      logLineToUse--;
    }
    logLineToUse++;

    if (logLineToUse >= document.getLineCount()) {
      editor.getCaretModel().moveToVisualPosition(new VisualPosition(logLineToUse, 0));
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
      if (logLineEndLog.softWrapLinesOnCurrentLogicalLine > 0) {
        moveCaretToStartOfSoftWrappedLine(editor, logLineEndVis);
      }
      else {
        int line = logLineEndVis.line;
        int column = 0;
        if (currentVisCaret.column == 0 && editorSettings.isSmartHome()) {
          findSmartIndentColumn(editor, line);
        }
        caretModel.moveToVisualPosition(new VisualPosition(line, column));
      }
    }

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  private static void moveCaretToStartOfSoftWrappedLine(Editor editor, VisualPosition currentVisual) {
    CaretModel caretModel = editor.getCaretModel();
    int line = currentVisual.line;
    int column = 0;

    if (currentVisual.column == 0) {
      line--;
      int nonSpaceColumn = findFirstNonSpaceColumnOnTheLine(editor, line);
      column = nonSpaceColumn >= 0 ? nonSpaceColumn : 0;
    }
    else {
      int nonSpaceColumn = findFirstNonSpaceColumnOnTheLine(editor, currentVisual.line);
      if (nonSpaceColumn > 0 /* current visual line is not empty */ && nonSpaceColumn < currentVisual.column) {
        column = nonSpaceColumn;
      }
    }

    caretModel.moveToVisualPosition(new VisualPosition(line, column));
  }

  private static int findSmartIndentColumn(Editor editor, int visualLine) {
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
   *                            <code>'-1'</code> otherwise
   */
  public static int findFirstNonSpaceColumnOnTheLine(Editor editor, int visualLineNumber) {
    Document document = editor.getDocument();
    VisualPosition visLine = new VisualPosition(visualLineNumber, 0);
    int logLine = editor.visualToLogicalPosition(visLine).line;
    int logLineStartOffset = document.getLineStartOffset(logLine);
    int logLineEndOffset = document.getLineEndOffset(logLine);
    LogicalPosition logLineStart = editor.offsetToLogicalPosition(logLineStartOffset);
    VisualPosition visLineStart = editor.logicalToVisualPosition(logLineStart);

    boolean softWrapIntroducedLine = visLineStart.line != visualLineNumber;
    if (!softWrapIntroducedLine) {
      int offset = findFirstNonSpaceOffsetInRange(document.getCharsSequence(), logLineStartOffset, logLineEndOffset);
      if (offset >= 0) {
        return EditorUtil.calcColumnNumber(editor, document.getCharsSequence(), logLineStartOffset, offset);
      }
      else {
        return -1;
      }
    }

    int lineFeedsToSkip = visualLineNumber - visLineStart.line;
    List<TextChange> softWraps = editor.getSoftWrapModel().getSoftWrapsForLine(logLine);
    for (TextChange softWrap : softWraps) {
      int softWrapLineFeedsNumber = StringUtil.countNewLines(softWrap.getText());

      if (softWrapLineFeedsNumber < lineFeedsToSkip) {
        lineFeedsToSkip -= softWrapLineFeedsNumber;
        continue;
      }

      // Point to the first non-white space symbol at the target soft wrap visual line or to the first non-white space symbol
      // of document line that follows it if possible.
      CharSequence softWrapText = softWrap.getText();
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

        end = findFirstNonSpaceOffsetInRange(document.getCharsSequence(), softWrap.getStart(), logLineEndOffset);
        if (end >= 0) {
          // Width of soft wrap virtual text that is located at the target visual line.
          int result = EditorUtil.calcColumnNumber(editor, softWrapText, j, softWrapTextLength);
          result++; // For column reserved for 'after soft wrap' sign.
          result += EditorUtil.calcColumnNumber(editor, document.getCharsSequence(), softWrap.getStart(), end);
          return result;
        }
        else {
          return -1;
        }
      }
    }
    return -1;
  }

  public static int findFirstNonSpaceOffsetOnTheLine(Document document, int lineNumber) {
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
   *                    <code>'-1'</code> otherwise
   */
  public static int findFirstNonSpaceOffsetInRange(CharSequence text, int start, int end) {
    for (; start < end; start++) {
      char c = text.charAt(start);
      if (c != ' ' && c != '\t') {
        return start;
      }
    }
    return -1;
  }

  public static void moveCaretToLineEnd(Editor editor, boolean isWithSelection) {
    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = selectionModel.hasBlockSelection()
                                          ? selectionModel.getBlockStart()
                                          : caretModel.getLogicalPosition();

    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    if (lineNumber >= document.getLineCount()) {
      LogicalPosition pos = new LogicalPosition(lineNumber, 0);
      editor.getCaretModel().moveToLogicalPosition(pos);
      setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      return;
    }
    VisualPosition currentVisualCaret = editor.getCaretModel().getVisualPosition();
    VisualPosition visualEndOfLineWithCaret
      = new VisualPosition(currentVisualCaret.line, EditorUtil.getLastVisualLineColumnNumber(editor, currentVisualCaret.line));

    // There is a possible case that the caret is already located at the visual end of line and the line is soft wrapped.
    // We want to move the caret to the end of the next visual line then.
    if (currentVisualCaret.equals(visualEndOfLineWithCaret)) {
      LogicalPosition logical = editor.visualToLogicalPosition(visualEndOfLineWithCaret);
      int offset = editor.logicalPositionToOffset(logical);
      if (offset < editor.getDocument().getTextLength()) {
        SoftWrapModel softWrapModel = editor.getSoftWrapModel();
        TextChange softWrap = softWrapModel.getSoftWrap(offset);
        if (softWrap == null) {
          // Same offset may correspond to positions on different visual lines in case of soft wraps presence
          // (all soft-wrap introduced virtual text is mapped to the same offset as the first document symbol after soft wrap).
          // Hence, we check for soft wraps presence at two offsets.
          softWrap = softWrapModel.getSoftWrap(offset + 1);
        }
        if (softWrap != null) {
          int line = currentVisualCaret.line + 1;
          int column = EditorUtil.getLastVisualLineColumnNumber(editor, line);
          visualEndOfLineWithCaret = new VisualPosition(line, column);
        }
      }
    }
    
    LogicalPosition logLineEnd = editor.visualToLogicalPosition(visualEndOfLineWithCaret);
    int offset = editor.logicalPositionToOffset(logLineEnd);
    lineNumber = logLineEnd.line;
    int newOffset = offset;

    CharSequence text = document.getCharsSequence();
    for (int i = newOffset - 1; i >= document.getLineStartOffset(lineNumber); i--) {
      if (text.charAt(i) != ' ' && text.charAt(i) != '\t') {
        break;
      }
      newOffset = i;
    }

    // Move to the calculated end of visual line if caret is located on a last non-white space symbols on a line and there are
    // remaining white space symbols.
    if (newOffset == offset || newOffset == caretModel.getOffset()) {
      caretModel.moveToVisualPosition(visualEndOfLineWithCaret);
    }
    else {
      caretModel.moveToOffset(newOffset);
    }

    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  public static void moveCaretToNextWord(Editor editor, boolean isWithSelection) {
    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = selectionModel.hasBlockSelection()
                                          ? selectionModel.getBlockStart()
                                          : caretModel.getLogicalPosition();

    int offset = caretModel.getOffset();
    CharSequence text = document.getCharsSequence();
    if (offset == document.getTextLength() - 1) {
      return;
    }
    int newOffset = offset + 1;
    int lineNumber = caretModel.getLogicalPosition().line;
    if (lineNumber >= document.getLineCount()) return;
    int maxOffset = document.getLineEndOffset(lineNumber);
    if (newOffset > maxOffset) {
      if (lineNumber + 1 >= document.getLineCount()) {
        return;
      }
      maxOffset = document.getLineEndOffset(lineNumber + 1);
    }
    boolean camel = editor.getSettings().isCamelWords();
    for (; newOffset < maxOffset; newOffset++) {
      if (isWordStart(text, newOffset, camel)) {
        break;
      }
    }
    caretModel.moveToOffset(newOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  private static void setupSelection(Editor editor,
                                     boolean isWithSelection,
                                     int selectionStart, LogicalPosition blockSelectionStart) {
    if (isWithSelection) {
      if (editor.isColumnMode()) {
        editor.getSelectionModel().setBlockSelection(blockSelectionStart, editor.getCaretModel().getLogicalPosition());
      }
      else {
        editor.getSelectionModel().setSelection(selectionStart, editor.getCaretModel().getOffset());
      }
    }
    else {
      editor.getSelectionModel().removeSelection();
    }
  }

  public static void moveCaretToPreviousWord(Editor editor, boolean isWithSelection) {
    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = selectionModel.hasBlockSelection()
                                          ? selectionModel.getBlockStart()
                                          : caretModel.getLogicalPosition();

    int offset = editor.getCaretModel().getOffset();
    if (offset == 0) return;

    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    CharSequence text = document.getCharsSequence();
    int newOffset = offset - 1;
    int minOffset = lineNumber > 0 ? document.getLineEndOffset(lineNumber - 1) : 0;
    boolean camel = editor.getSettings().isCamelWords();
    for (; newOffset > minOffset; newOffset--) {
      if (isWordStart(text, newOffset, camel)) break;
    }
    editor.getCaretModel().moveToOffset(newOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  public static void moveCaretPageUp(Editor editor, boolean isWithSelection) {
    ((EditorEx)editor).stopOptimizedScrolling();
    int lineHeight = editor.getLineHeight();
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    int linesIncrement = visibleArea.height / lineHeight;
    editor.getScrollingModel().scrollVertically(visibleArea.y - visibleArea.y % lineHeight - linesIncrement * lineHeight);
    int lineShift = -linesIncrement;
    editor.getCaretModel().moveCaretRelatively(0, lineShift, isWithSelection, editor.isColumnMode(), true);
  }

  public static void moveCaretPageDown(Editor editor, boolean isWithSelection) {
    ((EditorEx)editor).stopOptimizedScrolling();
    int lineHeight = editor.getLineHeight();
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    int linesIncrement = visibleArea.height / lineHeight;
    int allowedBottom = ((EditorEx)editor).getContentSize().height - visibleArea.height;
    editor.getScrollingModel().scrollVertically(
      Math.min(allowedBottom, visibleArea.y - visibleArea.y % lineHeight + linesIncrement * lineHeight));
    editor.getCaretModel().moveCaretRelatively(0, linesIncrement, isWithSelection, editor.isColumnMode(), true);
  }

  public static void moveCaretPageTop(Editor editor, boolean isWithSelection) {
    int lineHeight = editor.getLineHeight();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = selectionModel.hasBlockSelection()
                                          ? selectionModel.getBlockStart()
                                          : caretModel.getLogicalPosition();
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    int lineNumber = visibleArea.y / lineHeight;
    if (visibleArea.y % lineHeight > 0) {
      lineNumber++;
    }
    VisualPosition pos = new VisualPosition(lineNumber, editor.getCaretModel().getVisualPosition().column);
    editor.getCaretModel().moveToVisualPosition(pos);
    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  public static void moveCaretPageBottom(Editor editor, boolean isWithSelection) {
    int lineHeight = editor.getLineHeight();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = selectionModel.hasBlockSelection()
                                          ? selectionModel.getBlockStart()
                                          : caretModel.getLogicalPosition();
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    int lineNumber = (visibleArea.y + visibleArea.height) / lineHeight - 1;
    VisualPosition pos = new VisualPosition(lineNumber, editor.getCaretModel().getVisualPosition().column);
    editor.getCaretModel().moveToVisualPosition(pos);
    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  public static EditorPopupHandler createEditorPopupHandler(final String groupId) {
    return new EditorPopupHandler() {
      public void invokePopup(final EditorMouseEvent event) {
        if (!event.isConsumed() && event.getArea() == EditorMouseEventArea.EDITING_AREA) {
          ActionGroup group = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(groupId);
          ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_POPUP, group);
          MouseEvent e = event.getMouseEvent();
          final Component c = e.getComponent();
          if (c != null && c.isShowing()) {
            popupMenu.getComponent().show(c, e.getX(), e.getY());
          }
          e.consume();
        }
      }
    };
  }
}
