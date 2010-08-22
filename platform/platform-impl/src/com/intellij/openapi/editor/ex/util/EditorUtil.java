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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
        VisualPosition visual = editor.offsetToVisualPosition(softWrap.getStart() - 1);
        int result = visual.column;
        int x = editor.visualPositionToXY(visual).x;
        // We need to add width of the next symbol because current result column points to the last symbol before the soft wrap.
        return  result + textWidthInColumns(editor, text, softWrap.getStart() - 1, softWrap.getStart(), x);
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
        VisualPosition visual = editor.offsetToVisualPosition(nextSoftWrap.getStart() - 1);
        int result = visual.column;
        int x = editor.visualPositionToXY(visual).x;

        // We need to add symbol width because current column points to the last symbol before the next soft wrap;
        result += textWidthInColumns(editor, text, nextSoftWrap.getStart() - 1, nextSoftWrap.getStart(), x);

        int lineFeedIndex = StringUtil.indexOf(nextSoftWrap.getText(), '\n');
        result += textWidthInColumns(editor, nextSoftWrap.getText(), 0, lineFeedIndex, 0);
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
      VisualPosition visual = editor.offsetToVisualPosition(softWrap.getStart() - 1);
      int result = visual.column; // Column of the symbol just before the soft wrap
      int x = editor.visualPositionToXY(visual).x;

      // Target visual column is located on the last visual line of the current soft wrap.
      result += textWidthInColumns(editor, text, softWrap.getStart() - 1, softWrap.getStart(), x);
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

  /**
   * Allows to calculate offset of the given column assuming that it belongs to the given text line identified by the
   * given <code>[start; end)</code> intervals.
   *
   * @param editor        editor that is used for representing given text
   * @param text          target text
   * @param start         start offset of the logical line that holds target column (inclusive)
   * @param end           end offset of the logical line that holds target column (exclusive)
   * @param columnNumber  target column number
   * @param tabSize       number of desired visual columns to use for tabulation representation
   * @return              given text offset that identifies the same position that is pointed by the given visual column
   */
  public static int calcOffset(Editor editor, CharSequence text, int start, int end, int columnNumber, int tabSize) {
    final int maxScanIndex = Math.min(start + columnNumber + 1, end);
    SoftWrapModel softWrapModel = editor.getSoftWrapModel();
    List<? extends TextChange> softWraps = softWrapModel.getSoftWrapsForRange(start, maxScanIndex);
    int startToUse = start;
    int x = 0;
    AtomicInteger currentColumn = new AtomicInteger();
    for (TextChange softWrap : softWraps) {
      // There is a possible case that target column points inside soft wrap-introduced virtual space.
      if (currentColumn.get() >= columnNumber) {
        return startToUse;
      }
      int result = calcSoftWrapUnawareOffset(editor, text, startToUse, softWrap.getEnd(), columnNumber, tabSize, x, currentColumn);
      if (result >= 0) {
        return result;
      }

      startToUse = softWrap.getStart();
      x = softWrapModel.getSoftWrapIndentWidthInPixels(softWrap);
    }

    // There is a possible case that target column points inside soft wrap-introduced virtual space.
    if (currentColumn.get() >= columnNumber) {
      return startToUse;
    }

    int result = calcSoftWrapUnawareOffset(editor, text, startToUse, end, columnNumber, tabSize, x, currentColumn);
    if (result >= 0) {
      return result;
    }

    // We assume that given column points to the virtual space after the line end if control flow reaches this place,
    // hence, just return end of line offset then.
    return end;
  }

  /**
   * Tries to match given logical column to the document offset assuming that it's located at <code>[start; end)</code> region.
   *
   * @param editor          editor that is used to represent target document
   * @param text            target document text
   * @param start           start offset to check (inclusive)
   * @param end             end offset to check (exclusive)
   * @param columnNumber    target logical column number
   * @param tabSize         user-defined desired number of columns to use for tabulation symbol representation
   * @param x               <code>'x'</code> coordinate that corresponds to the given <code>'start'</code> offset
   * @param currentColumn   logical column that corresponds to the given <code>'start'</code> offset
   * @return                target offset that belongs to the <code>[start; end)</code> range and points to the target logical
   *                        column if any; <code>-1</code> otherwise
   */
  private static int calcSoftWrapUnawareOffset(Editor editor, CharSequence text, int start, int end, int columnNumber, int tabSize, int x,
                                               AtomicInteger currentColumn)
  {
    // The main problem in a calculation is that target text may contain tabulation symbols and every such symbol may take different
    // number of logical columns to represent. E.g. it takes two columns if tab size is four and current column is two; three columns
    // if tab size is four and current column is one etc. So, first of all we check if there are tabulation symbols at the target
    // text fragment.
    boolean useOptimization = true;
    boolean hasNonTabs = false;
    boolean hasTabs = false;
    for (int i = start; i < end; i++) {
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

    // Perform optimized processing if possible. 'Optimized' here means the processing when we exactly know how many logical
    // columns are occupied by tabulation symbols.
    if (editor == null || useOptimization) {
      if (!hasTabs) {
        int result = start + columnNumber - currentColumn.get();
        if (result < end) {
          return result;
        }
        else {
          currentColumn.addAndGet(end - start);
          return -1;
        }
      }

      // This variable holds number of 'virtual' tab-introduced columns, e.g. there is a possible case that particular tab owns
      // three columns, hence, it increases 'shift' by two (3 - 1).
      int shift = 0;
      int offset = start;
      int prevX = x;
      for (; offset < end && offset + shift + currentColumn.get() < start + columnNumber; offset++) {
        if (text.charAt(offset) == '\t') {
          int nextX = nextTabStop(prevX, editor, tabSize);
          shift += columnsNumber(nextX - prevX, getSpaceWidth(Font.PLAIN, editor)) - 1;
          prevX = nextX;
        }
      }
      int diff = start + columnNumber - offset - shift - currentColumn.get();
      if (diff < 0) {
        return offset - 1;
      }
      else if (diff == 0) {
        return offset;
      }
      else {
        currentColumn.addAndGet(offset - start + shift);
        return -1;
      }
    }

    // It means that there are tabulation symbols that can't be explicitly mapped to the occupied logical columns number,
    // hence, we need to perform special calculations to get know that.
    EditorEx editorImpl = (EditorEx)editor;
    int offset = start;
    IterationState state = new IterationState(editorImpl, offset, false);
    int fontType = state.getMergedAttributes().getFontType();
    int column = currentColumn.get();
    int spaceSize = getSpaceWidth(fontType, editorImpl);
    for (; column < columnNumber && offset < end; offset++) {
      if (offset >= state.getEndOffset()) {
        state.advance();
        fontType = state.getMergedAttributes().getFontType();
      }

      char c = text.charAt(offset);
      if (c == '\t') {
        int prevX = x;
        x = nextTabStop(x, editorImpl);
        column += columnsNumber(x - prevX, spaceSize);
      }
      else {
        x += charWidth(c, fontType, editorImpl);
        column++;
      }
    }

    if (column == columnNumber) {
      return offset;
    }
    if (column > columnNumber && offset > 0 && text.charAt(offset - 1) == '\t') {
      return offset - 1;
    }
    currentColumn.set(column);
    return -1;
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
    if (editor != null) {
      TextChange softWrap = editor.getSoftWrapModel().getSoftWrap(start);
      useOptimization = softWrap == null;
    }
    boolean hasNonTabs = false;
    if (useOptimization) {
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
    return nextTabStop(x, editor, tabSize);
  }

  public static int nextTabStop(int x, Editor editor, int tabSize) {
    if (tabSize <= 0) {
      return x + getSpaceWidth(Font.PLAIN, editor);
    }
    tabSize *= getSpaceWidth(Font.PLAIN, editor);

    int nTabs = x / tabSize;
    return (nTabs + 1) * tabSize;
  }

  public static int textWidthInColumns(@NotNull Editor editor, CharSequence text, int start, int end, int x) {
    int startToUse = start;

    // Skip all lines except the last.
    for (int i = StringUtil.lastIndexOf(text, '\n', startToUse, end); i >= 0; i = StringUtil.lastIndexOf(text, '\n', startToUse, end)) {
      startToUse = i + 1;
    }

    // Tabulation is assumed to be the only symbol which representation may take various number of visual columns, hence,
    // we return eagerly if no such symbol is found.
    int lastTabSymbolIndex = StringUtil.lastIndexOf(text, '\t', startToUse, end);
    if (lastTabSymbolIndex < 0) {
      return end - startToUse;
    }

    int result = 0;
    int prevX;
    int spaceSize = getSpaceWidth(Font.PLAIN, editor);

    // Calculate number of columns up to the latest tabulation symbol.
    for (int i = startToUse; i <= lastTabSymbolIndex; i++) {
      char c = text.charAt(i);
      prevX = x;
      switch (c) {
        case '\t': 
          x = nextTabStop(x, editor);
          result += columnsNumber(x - prevX, spaceSize);
          break;
        case '\n': x = result = 0; break;
        default: x += charWidth(c, Font.PLAIN, editor); result++;
      }
    }

    // Add remaining tabulation-free columns.
    result += end - lastTabSymbolIndex - 1;
    return result;
  }

  /**
   * Allows to answer how many columns are necessary for representation of the given char on a screen.
   *
   * @param c           target char
   * @param x           <code>'x'</code> coordinate of the line where given char is represented that indicates char end location
   * @param prevX       <code>'x'</code> coordinate of the line where given char is represented that indicates char start location
   * @param spaceSize   <code>'space'</code> symbol width
   * @return            number of columns necessary for representation of the given char on a screen.
   */
  public static int columnsNumber(char c, int x, int prevX, int spaceSize) {
    if (c != '\t') {
      return 1;
    }
    int result = (x - prevX) / spaceSize;
    if ((x - prevX) % spaceSize > 0) {
      result++;
    }
    return result;
  }

  /**
   * Allows to answer how many visual columns are occupied by the given width.
   *
   * @param width       target width
   * @param spaceSize   width of the single space symbol within the target editor
   * @return            number of visual columns are occupied by the given width
   */
  public static int columnsNumber(int width, int spaceSize) {
    int result = width / spaceSize;
    if (width % spaceSize > 0) {
      result++;
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
  public static int textWidth(@NotNull Editor editor, CharSequence text, int start, int end, int fontType, int x) {
    int result = 0;
    for (int i = start; i < end; i++) {
      char c = text.charAt(i);
      if (c != '\t') {
        FontInfo font = fontForChar(c, fontType, editor);
        result += font.charWidth(c, editor.getContentComponent());
        continue;
      }

      result += nextTabStop(x + result, editor) - result - x;
    }
    return result;
  }

  /**
   * Calculates the closest non-soft-wrapped logical positions for current caret position.
   *
   * @param editor    target editor to use
   * @return          pair of non-soft-wrapped logical positions closest to the caret position of the given editor
   */
  public static Pair<LogicalPosition, LogicalPosition> calcCaretLinesRange(Editor editor) {
    VisualPosition caret = editor.getCaretModel().getVisualPosition();
    int visualLine = caret.line;

    LogicalPosition lineStart = editor.visualToLogicalPosition(new VisualPosition(visualLine, 0));
    while (lineStart.softWrapLinesOnCurrentLogicalLine > 0) {
      lineStart = editor.visualToLogicalPosition(new VisualPosition(--visualLine, 0));
    }

    visualLine = caret.line + 1;
    LogicalPosition nextLineStart = editor.visualToLogicalPosition(new VisualPosition(caret.line + 1, 0));
    while (nextLineStart.line == lineStart.line) {
      nextLineStart = editor.visualToLogicalPosition(new VisualPosition(++visualLine, 0));
    }
    return new Pair<LogicalPosition, LogicalPosition>(lineStart, nextLineStart);
  }
}


