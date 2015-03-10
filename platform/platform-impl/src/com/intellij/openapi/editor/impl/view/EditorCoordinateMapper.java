/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

class EditorCoordinateMapper {
  private final EditorView myView;
  private final Document myDocument;
  
  EditorCoordinateMapper(EditorView view) {
    myView = view;
    myDocument = myView.getEditor().getDocument();
  }

  int visualLineToY(int line) {
    if (line < 0) throw new IndexOutOfBoundsException("Wrong line: " + line);
    return line * myView.getLineHeight();
  }

  int yToVisualLine(int y) {
    assert y >= 0 : y;
    return y / myView.getLineHeight();
  }

  @NotNull
  LogicalPosition offsetToLogicalPosition(int offset) {
    int textLength = myDocument.getTextLength();
    if (offset < 0 || textLength == 0) {
      return new LogicalPosition(0, 0);
    }
    offset = Math.min(offset, textLength);
    int line = myDocument.getLineNumber(offset);
    LineLayout lineLayout = myView.getLineLayout(line);
    float x = line == 0 ? myView.getPrefixTextWidthInPixels() : 0;
    int relOffset = offset - myDocument.getLineStartOffset(line);
    int column = 0;
    for (LineLayout.Fragment fragment : lineLayout.getFragmentsInLogicalOrder()) {
      if (relOffset >= fragment.getStartOffset() && relOffset <= fragment.getEndOffset()) {
        column += fragment.offsetToColumn(x, relOffset - fragment.getStartOffset());
        break;
      }
      else {
        column += fragment.getColumnCount(x);
        x = fragment.advance(x);
      }
    }
    return new LogicalPosition(line, column);
  }

  int logicalPositionToOffset(@NotNull LogicalPosition pos) {
    int line = pos.line;
    if (line >= myDocument.getLineCount()) {
      return myDocument.getTextLength();
    }
    else {
      LineLayout lineLayout = myView.getLineLayout(line);
      float x = line == 0 ? myView.getPrefixTextWidthInPixels() : 0;
      int column = pos.column;
      int relOffset = 0;
      for (LineLayout.Fragment fragment : lineLayout.getFragmentsInLogicalOrder()) {
        int columnCount = fragment.getColumnCount(x);
        if (column <= columnCount) {
          relOffset += fragment.columnToOffset(x, column);
          break;
        }
        else {
          relOffset += fragment.getLength();
          column -= columnCount;
          x = fragment.advance(x);
        }
      }
      return myDocument.getLineStartOffset(line) + relOffset;
    }
  }
  
  @NotNull
  VisualPosition logicalToVisualPosition(@NotNull LogicalPosition pos) {
    return new VisualPosition(pos.line, pos.column);
  }

  @NotNull
  LogicalPosition visualToLogicalPosition(@NotNull VisualPosition pos) {
    return new LogicalPosition(pos.line, pos.column);
  }

  @NotNull
  VisualPosition offsetToVisualPosition(int offset) {
    return logicalToVisualPosition(offsetToLogicalPosition(offset));
  }

  int offsetToVisualLine(int offset) {
    if (offset <= 0) {
      return 0;
    }
    if (offset >= myDocument.getTextLength()) {
      return Math.max(0, myDocument.getLineCount() - 1);
    }
    return myDocument.getLineNumber(offset);
  }

  @NotNull
  VisualPosition xyToVisualPosition(@NotNull Point p) {
    int line = yToVisualLine(Math.max(p.y, 0));
    float x = line == 0 ? myView.getPrefixTextWidthInPixels() : 0;
    int column = 0;
    if (line < myDocument.getLineCount()) {
      LineLayout lineLayout = myView.getLineLayout(line);
      int lastEndOffset = 0;
      for (LineLayout.Fragment fragment : lineLayout.getFragmentsInVisualOrder()) {
        column += fragment.getVisualStartOffset() - lastEndOffset;
        lastEndOffset = fragment.getVisualEndOffset();
        float nextX = fragment.advance(x);
        if (p.x <= nextX) {
          column = fragment.isRtl() ? column - fragment.xToColumn(x, p.x) : column + fragment.xToColumn(x, p.x);
          return new VisualPosition(line, column);
        }
        else {
          column = fragment.isRtl() ? column - fragment.getColumnCount(x) : column + fragment.getColumnCount(x);
          x = nextX;
        }
      }
      int lineLength = myDocument.getLineEndOffset(line) - myDocument.getLineStartOffset(line);
      column += lineLength - lastEndOffset;
    }
    return new VisualPosition(line, column + spacePixelsToColumns((int)(p.x - x)));
  }

  @NotNull
  Point visualPositionToXY(@NotNull VisualPosition pos, boolean leanTowardsLargerColumns) {
    int y = visualLineToY(pos.line);
    float x = pos.line == 0 ? myView.getPrefixTextWidthInPixels() : 0;
    int column = pos.column;
    if (pos.line < myDocument.getLineCount() && (column > 0 || leanTowardsLargerColumns)) {
      LineLayout lineLayout = myView.getLineLayout(pos.line);
      int lastEndOffset = 0;
      for (LineLayout.Fragment fragment : lineLayout.getFragmentsInVisualOrder()) {
        column -= fragment.getVisualStartOffset() - lastEndOffset;
        lastEndOffset = fragment.getVisualEndOffset();
        int nextColumn = fragment.isRtl() ? column + fragment.getColumnCount(x) : column - fragment.getColumnCount(x);
        if (column > 0 && nextColumn < 0 || 
            column < 0 && nextColumn > 0 || 
            Math.max(column, nextColumn) == 0 && leanTowardsLargerColumns ||       
            Math.min(column, nextColumn) == 0 && !leanTowardsLargerColumns) {
          x = fragment.columnToX(x, Math.abs(column));
          return new Point((int)x, y);
        }
        else {
          column = nextColumn;
          x = fragment.advance(x);
        }
      }
      int lineLength = myDocument.getLineEndOffset(pos.line) - myDocument.getLineStartOffset(pos.line);
      column -= lineLength - lastEndOffset;
    }
    return new Point((int)(spaceColumnsToPixels(column) + x), y);
  }

  @NotNull
  Point offsetToXY(int offset, boolean leanTowardsLargerOffsets) {
    offset = Math.max(0, Math.min(myDocument.getTextLength(), offset));
    int line = myDocument.getLineNumber(offset);
    int intraLineOffset = offset - myDocument.getLineStartOffset(line);
    int y = visualLineToY(line);
    float x = line == 0 ? myView.getPrefixTextWidthInPixels() : 0;
    if (myDocument.getTextLength() > 0 && (intraLineOffset > 0 || leanTowardsLargerOffsets)) {
      LineLayout lineLayout = myView.getLineLayout(line);
      for (LineLayout.Fragment fragment : lineLayout.getFragmentsInVisualOrder()) {
        if (intraLineOffset > fragment.getStartOffset() && intraLineOffset < fragment.getEndOffset() ||
            intraLineOffset == fragment.getStartOffset() && leanTowardsLargerOffsets ||
            intraLineOffset == fragment.getEndOffset() && !leanTowardsLargerOffsets) {
          x = fragment.absoluteOffsetToX(x, intraLineOffset);
          break;
        }
        else {
          x = fragment.advance(x);
        }
      }
    }
    return new Point((int)x, y);
  }

  private int spacePixelsToColumns(int pixels) {
    int plainSpaceWidth = myView.getPlainSpaceWidth();
    return pixels < 0 ? 0 : (pixels + plainSpaceWidth / 2) / plainSpaceWidth;
  }

  private int spaceColumnsToPixels(int columns) {
    return columns < 0 ? 0 : columns * myView.getPlainSpaceWidth();
  }
}
