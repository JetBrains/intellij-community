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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.impl.IterationState;

import java.awt.*;

public class EditorUtil {
  private EditorUtil() { }

  public static int getLastVisualLineColumnNumber(Editor editor, int line) {
    VisualPosition visStart = new VisualPosition(line, 0);
    LogicalPosition logStart = editor.visualToLogicalPosition(visStart);
    int lastLogLine = logStart.line;
    while (lastLogLine < editor.getDocument().getLineCount() - 1) {
      logStart = new LogicalPosition(logStart.line + 1, logStart.column);
      VisualPosition tryVisible = editor.logicalToVisualPosition(logStart);
      if (tryVisible.line != visStart.line) break;
      lastLogLine = logStart.line;
    }

    int lastLine = editor.getDocument().getLineCount() - 1;
    if (lastLine < 0) {
      return 0;
    }
    return editor.offsetToVisualPosition(editor.getDocument().getLineEndOffset(Math.min(lastLogLine, lastLine))).column;
  }

  public static float calcVerticalScrollProportion(Editor editor) {
    Rectangle viewRect = editor.getScrollingModel().getVisibleAreaOnScrollingFinished();
    if (viewRect.height == 0) {
      return 0;
    }
    LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
    Point location = editor.logicalPositionToXY(pos);
    return (location.y - viewRect.y) / (float) viewRect.height;
  }

  public static void setVerticalScrollProportion(Editor editor, float proportion) {
    Rectangle viewRect = editor.getScrollingModel().getVisibleArea();
    LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();
    Point caretLocation = editor.logicalPositionToXY(caretPosition);
    int yPos = caretLocation.y;
    yPos -= viewRect.height * proportion;
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
}


