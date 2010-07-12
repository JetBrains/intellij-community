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
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.impl.IterationState;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

public class EditorUtil {
  private EditorUtil() { }

  public static int getLastVisualLineColumnNumber(Editor editor, int line) {
    Document document = editor.getDocument();
    int lastLine = document.getLineCount() - 1;
    if (lastLine < 0) {
      return 0;
    }

    // Filter all lines that are not shown because of collapsed folding region.
    VisualPosition visStart = new VisualPosition(line, 0);
    LogicalPosition logStart = editor.visualToLogicalPosition(visStart);
    int lastLogLine = logStart.line;
    while (lastLogLine < document.getLineCount() - 1) {
      logStart = new LogicalPosition(logStart.line + 1, logStart.column);
      VisualPosition tryVisible = editor.logicalToVisualPosition(logStart);
      if (tryVisible.line != visStart.line) break;
      lastLogLine = logStart.line;
    }

    int resultLogLine = Math.min(lastLogLine, lastLine);
    VisualPosition resVisStart = editor.offsetToVisualPosition(document.getLineStartOffset(resultLogLine));
    VisualPosition resVisEnd = editor.offsetToVisualPosition(document.getLineEndOffset(resultLogLine));

    // Target logical line is not soft wrap affected.
    if (resVisStart.line == resVisEnd.line) {
      return resVisEnd.column;
    }

    int visualLinesToSkip = line - resVisStart.line;
    List<? extends TextChange> softWraps = editor.getSoftWrapModel().getSoftWrapsForLine(resultLogLine);
    for (int i = 0; i < softWraps.size(); i++) {
      TextChange softWrap = softWraps.get(i);
      CharSequence text = document.getCharsSequence();
      if (visualLinesToSkip <= 0) {
        int result = editor.offsetToVisualPosition(softWrap.getStart() - 1).column;
        // We need to add width of the next symbol because current result column points to the last symbol before the soft wrap.
        return  result + textWidthInColumns(editor, text, softWrap.getStart() - 1, softWrap.getStart(), result);
      }

      int softWrapLineFeeds = StringUtil.countNewLines(softWrap.getText());
      if (softWrapLineFeeds < visualLinesToSkip) {
        visualLinesToSkip -= softWrapLineFeeds;
        continue;
      }

      // Target visual column is located on the last visual line of the current soft wrap.
      if (softWrapLineFeeds == visualLinesToSkip) {
        if (i >= softWraps.size() - 1) {
          return resVisEnd.column;
        }
        // We need to find visual column for line feed of the next soft wrap.
        TextChange nextSoftWrap = softWraps.get(i + 1);
        int result = editor.offsetToVisualPosition(nextSoftWrap.getStart() - 1).column;

        // We need to add symbol width because current column points to the last symbol before the next soft wrap;
        result += textWidthInColumns(editor, text, nextSoftWrap.getStart() - 1, nextSoftWrap.getStart(), result);

        int lineFeedIndex = StringUtil.indexOf(nextSoftWrap.getText(), '\n');
        result += textWidthInColumns(editor, nextSoftWrap.getText(), 0, lineFeedIndex, result);
        return result;
      }

      // Target visual column is the one before line feed introduced by the current soft wrap.
      int softWrapStartOffset = 0;
      int softWrapEndOffset = 0;
      int softWrapTextLength = softWrap.getText().length();
      while (visualLinesToSkip-- > 0) {
        softWrapStartOffset = softWrapEndOffset + 1;
        if (softWrapStartOffset >= softWrapTextLength) {
          assert false;
          return resVisEnd.column;
        }
        softWrapEndOffset = StringUtil.indexOf(softWrap.getText(), '\n', softWrapStartOffset, softWrapTextLength);
        if (softWrapEndOffset < 0) {
          assert false;
          return resVisEnd.column;
        }
      }
      int result = editor.offsetToVisualPosition(softWrap.getStart() - 1).column; // Column of the symbol just before the soft wrap
      // Target visual column is located on the last visual line of the current soft wrap.
      result += textWidthInColumns(editor, text, softWrap.getStart() - 1, softWrap.getStart(), result);
      result += calcColumnNumber(editor, softWrap.getText(), softWrapStartOffset, softWrapEndOffset);
      return result;
    }

    assert false;
    return resVisEnd.column;
  }

  public static float calcVerticalScrollProportion(Editor editor) {
    Rectangle viewArea = editor.getScrollingModel().getVisibleAreaOnScrollingFinished();
    if (viewArea.height == 0) {
      return 0;
    }
    LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
    Point location = editor.logicalPositionToXY(pos);
    return (location.y - viewArea.y) / (float) viewArea.height;
  }

  public static void setVerticalScrollProportion(Editor editor, float proportion) {
    Rectangle viewArea = editor.getScrollingModel().getVisibleArea();
    LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();
    Point caretLocation = editor.logicalPositionToXY(caretPosition);
    int yPos = caretLocation.y;
    yPos -= viewArea.height * proportion;
    editor.getScrollingModel().scrollVertically(yPos);
  }

  public static void fillVirtualSpaceUntilCaret(final Editor editor) {
    final LogicalPosition position = editor.getCaretModel().getLogicalPosition();
    fillVirtualSpaceUntil(editor, position.column, position.line);
  }

  public static void fillVirtualSpaceUntil(final Editor editor, int columnNumber, int lineNumber) {
    final int offset = editor.logicalPositionToOffset(new LogicalPosition(lineNumber, columnNumber));
    final String filler = EditorModificationUtil.calcStringToFillVirtualSpace(editor);
    if (filler.length() > 0) {
      new WriteAction(){
        protected void run(final Result result) throws Throwable {
          editor.getDocument().insertString(offset, filler);
          editor.getCaretModel().moveToOffset(offset + filler.length());
        }
      }.execute();
    }
  }

  public static int calcOffset(Editor editor, CharSequence text, int start, int end, int columnNumber, int tabSize) {
    // If all tabs here goes before any other chars in the line we may use an optimization here.
    boolean useOptimization = true;
    boolean hasNonTabs = false;
    boolean hasTabs = false;
    final int maxScanIndex = Math.min(start + columnNumber + 1, end);

    for (int i = start; i < maxScanIndex; i++) {
      if (text.charAt(i) == '\t') {
        hasTabs = true;
        if (hasNonTabs) {
          useOptimization = false;
          break;
        }
      } else {
        hasNonTabs = true;
      }
    }

    if (editor == null || useOptimization) {
      if (!hasTabs) return Math.min(start + columnNumber, end);

      int shift = 0;
      int offset = start;
      for (; offset < end && offset + shift < start + columnNumber; offset++) {
        if (text.charAt(offset) == '\t') {
          shift += getTabLength(offset + shift - start, tabSize) - 1;
        }
      }
      if (offset + shift > start + columnNumber) {
        offset--;
      }

      return offset;
    }

    EditorEx editorImpl = (EditorEx)editor;
    int offset = start;
    IterationState state = new IterationState(editorImpl, offset, false);
    int fontType = state.getMergedAttributes().getFontType();
    int column = 0;
    int x = 0;
    int spaceSize = getSpaceWidth(fontType, editorImpl);
    while (column < columnNumber) {
      if (offset >= state.getEndOffset()) {
        state.advance();

        fontType = state.getMergedAttributes().getFontType();
      }

      char c = offset < end ? text.charAt(offset++) : ' ';
      if (c == '\t') {
        int prevX = x;
        x = nextTabStop(x, editorImpl);
        column += (x - prevX) / spaceSize;
      }
      else {
        x += charWidth(c, fontType, editorImpl);
        column++;
      }
    }
    if (column == columnNumber && offset < end && text.charAt(offset) == '\t' && (nextTabStop(x, editorImpl) - x) / spaceSize == 0) {
      offset++;
    }
    if (column > columnNumber) offset--;

    return offset;
  }

  private static int getTabLength(int colNumber, int tabSize) {
    if (tabSize <= 0) {
      tabSize = 1;
    }
    return tabSize - colNumber % tabSize;
  }

  public static int calcColumnNumber(Editor editor, CharSequence text, int start, int offset) {
    return calcColumnNumber(editor, text, start, offset, getTabSize(editor));
  }

  public static int calcColumnNumber(Editor editor, CharSequence text, int start, int offset, int tabSize) {
    boolean useOptimization = true;
    boolean hasNonTabs = false;
    for (int i = start; i < offset; i++) {
      if (text.charAt(i) == '\t') {
        if (hasNonTabs) {
          useOptimization = false;
          break;
        }
      } else {
        hasNonTabs = true;
      }
    }

    if (editor == null || useOptimization) {
      int shift = 0;

      for (int i = start; i < offset; i++) {
        char c = text.charAt(i);
        assert c != '\n' && c != '\r';
        if (c == '\t') {
          shift += getTabLength(i + shift - start, tabSize) - 1;
        }
      }
      return offset - start + shift;
    }

    EditorEx editorImpl = (EditorEx) editor;
    return editorImpl.calcColumnNumber(text, start, offset, tabSize);
  }

  public static void setHandCursor(Editor view) {
    Cursor c = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    // XXX: Workaround, simply view.getContentComponent().setCursor(c) doesn't work
    if (view.getContentComponent().getCursor() != c) {
      view.getContentComponent().setCursor(c);
    }
  }

  public static FontInfo fontForChar(final char c, int style, Editor editor) {
    EditorColorsScheme colorsScheme = editor.getColorsScheme();
    return ComplementaryFontsRegistry.getFontAbleToDisplay(c, colorsScheme.getEditorFontSize(), style, colorsScheme.getEditorFontName());
  }

  public static int charWidth(char c, int fontType, Editor editor) {
    return fontForChar(c, fontType, editor).charWidth(c, editor.getContentComponent());
  }

  public static int getSpaceWidth(int fontType, Editor editor) {
    int width = charWidth(' ', fontType, editor);
    return width > 0 ? width : 1;
  }

  public static int getTabSize(Editor editor) {
    return editor.getSettings().getTabSize(editor.getProject());
  }

  public static int nextTabStop(int x, Editor editor) {
    int tabSize = getTabSize(editor);
    if (tabSize <= 0) {
      tabSize = 1;
    }

    tabSize *= getSpaceWidth(Font.PLAIN, editor);

    int nTabs = x / tabSize;
    return (nTabs + 1) * tabSize;
  }

  /**
   * Allows to answer how many columns are used to represent tabulation symbols that is started at the given visual column
   * at the given editor.
   *
   * @param visualColumn    visual column where target tabulation symbol starts
   * @param editor          target editor where tabulation symbol is to be represented
   * @return                number of visual columns required to represent tabulation symbols that starts at the given column
   */
  public static int tabWidthInColumns(@NotNull Editor editor, int visualColumn) {
    if (!editor.getSettings().isWhitespacesShown()) {
      return 1;
    }
    int tabSize = getTabSize(editor);
    int tabsNumber = visualColumn / tabSize;
    return (tabsNumber + 1) * tabSize - visualColumn;
  }

  public static int textWidthInColumns(@NotNull Editor editor, CharSequence text, int start, int end, int columnOffset) {
    int result = 0;
    for (int i = start; i < end; i++) {
      if (text.charAt(i) == '\t') {
        result += tabWidthInColumns(editor, columnOffset + result);
      }
      else {
        result++;
      }
    }
    return result;
  }

  /**
   * Allows to answer what width in pixels is required to draw fragment of the given char array from <code>[start; end)</code> interval
   * at the given editor.
   * <p/>
   * Tabulation symbols is processed specially, i.e. it's ta
   * <p/>
   * <b>Note:</b> it's assumed that target text fragment remains to the single line, i.e. line feed symbols within it are not
   * treated specially.
   *
   * @param editor    editor that will be used for target text representation
   * @param text      target text holder
   * @param start     offset within the given char array that points to target text start (inclusive)
   * @param end       offset within the given char array that points to target text end (exclusive)
   * @param fontType  font type to use for target text representation
   * @param x         <code>'x'</code> coordinate that should be used as a starting point for target text representation.
   *                  It's necessity is implied by the fact that IDEA editor may represent tabulation symbols in any range
   *                  from <code>[1; tab size]</code> (check {@link #nextTabStop(int, Editor)} for more details)
   * @return          width in pixels required for target text representation
   */
  public static int textWidth(@NotNull Editor editor, char[] text, int start, int end, int fontType, int x) {
    int result = 0;
    for (int i = start; i < end; i++) {
      char c = text[i];
      if (c != '\t') {
        FontInfo font = fontForChar(c, fontType, editor);
        result += font.charWidth(c, editor.getContentComponent());
        continue;
      }

      if (editor.getSettings().isWhitespacesShown()) {
        result += nextTabStop(x + result, editor) - result - x;
      }
      else {
        result += getSpaceWidth(fontType, editor);
      }
    }
    return result;
  }
}


