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
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.impl.FoldingModelImpl;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Performs transformations between various location representations in editor 
 * (offset, logical position, visual position, pixel coordinates).
 * 
 * @see LogicalPosition
 * @see VisualPosition
 */
class EditorCoordinateMapper {
  private final EditorView myView;
  private final Document myDocument;
  private final FoldingModelImpl myFoldingModel;
  
  EditorCoordinateMapper(EditorView view) {
    myView = view;
    myDocument = myView.getEditor().getDocument();
    myFoldingModel = myView.getEditor().getFoldingModel();
  }

  int visualLineToY(int line) {
    return line * myView.getLineHeight();
  }

  int yToVisualLine(int y) {
    return y / myView.getLineHeight();
  }

  @NotNull
  LogicalPosition offsetToLogicalPosition(int offset) {
    int textLength = myDocument.getTextLength();
    if (offset <= 0 || textLength == 0) {
      return new LogicalPosition(0, 0);
    }
    offset = Math.min(offset, textLength);
    int line = myDocument.getLineNumber(offset);
    offset = Math.min(offset, myDocument.getLineEndOffset(line));
    int column = 0;
    CharSequence text = myDocument.getImmutableCharSequence();
    int tabSize = myView.getTabSize();
    for (int i = myDocument.getLineStartOffset(line); i < offset; i++) {
      if (text.charAt(i) == '\t') {
        column = (column / tabSize + 1) * tabSize;
      }
      else {
        column++;
      }
    }
    return new LogicalPosition(line, column);
  }

  int logicalPositionToOffset(@NotNull LogicalPosition pos) {
    int line = pos.line;
    if (line >= myDocument.getLineCount()) return myDocument.getTextLength();
    
    int lineStartOffset = myDocument.getLineStartOffset(line);
    int lineEndOffset = myDocument.getLineEndOffset(line);
    CharSequence text = myDocument.getImmutableCharSequence();
    int tabSize = myView.getTabSize();
    int column = 0;
    for (int i = lineStartOffset; i < lineEndOffset; i++) {
      if (text.charAt(i) == '\t') {
        column = (column / tabSize + 1) * tabSize;
      }
      else {
        column++;
      }
      if (pos.column < column) return i;
    }
    return lineEndOffset;
  }

  @NotNull
  VisualPosition logicalToVisualPosition(@NotNull LogicalPosition pos) {
    int line = pos.line;
    int column = pos.column;
    int logicalLineCount = myDocument.getLineCount();
    if (line >= logicalLineCount) {
      return new VisualPosition(line - logicalLineCount + myView.getEditor().getVisibleLineCount(), column, pos.leansForward);
    }
    int offset = logicalPositionToOffset(pos);
    int visualLine = offsetToVisualLine(offset);
    int maxVisualColumn = 0;
    int maxLogicalColumn = 0;
    for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, offset)) {
      if (column == 0 && !pos.leansForward && 
          fragment.getStartVisualColumn() == 0 && fragment.getStartLogicalLine() == line) {
        return new VisualPosition(visualLine, 0);
      }
      if (fragment.isCollapsedFoldRegion()) {
        int startLogicalLine = fragment.getStartLogicalLine();
        int endLogicalLine = fragment.getEndLogicalLine();
        int startLogicalColumn = fragment.getStartLogicalColumn();
        int endLogicalColumn = fragment.getEndLogicalColumn();
        if ((line > startLogicalLine || line == startLogicalLine && (column > startLogicalColumn || 
                                                                     column == startLogicalColumn && pos.leansForward)) && 
            (line < endLogicalLine || line == endLogicalLine && column < endLogicalColumn)) {
          return new VisualPosition(visualLine, fragment.getStartVisualColumn(), true);
        }
        if (line == endLogicalLine && column == endLogicalColumn && !pos.leansForward) {
          return new VisualPosition(visualLine, fragment.getEndVisualColumn());
        }
        maxLogicalColumn = startLogicalLine == endLogicalLine ? Math.max(maxLogicalColumn, endLogicalColumn) : endLogicalColumn;
      }
      else {
        int minColumn = fragment.getMinLogicalColumn();
        int maxColumn = fragment.getMaxLogicalColumn();
        if (line == fragment.getStartLogicalLine() &&
            (column > minColumn && column < maxColumn ||
             column == minColumn && pos.leansForward ||
             column == maxColumn && !pos.leansForward)) {
          return new VisualPosition(visualLine, fragment.logicalToVisualColumn(column), fragment.isRtl() ^ pos.leansForward);
        }
        maxLogicalColumn = Math.max(maxLogicalColumn, maxColumn);
      }
      maxVisualColumn = fragment.getEndVisualColumn();
    }
    return new VisualPosition(visualLine, column - maxLogicalColumn + maxVisualColumn, pos.leansForward);
  }

  @NotNull
  LogicalPosition visualToLogicalPosition(@NotNull VisualPosition pos) {
    int line = pos.line;
    int column = pos.column;
    int logicalLine = visualToLogicalLine(line);
    if (logicalLine >= myDocument.getLineCount()) {
      return new LogicalPosition(logicalLine, column, pos.leansRight);
    }
    if (column == 0 && !pos.leansRight) {
      return new LogicalPosition(logicalLine, 0);
    }
    int offset = myDocument.getLineStartOffset(logicalLine);
    int maxVisualColumn = 0;
    int maxLogicalColumn = 0;
    for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, offset)) {
      int minColumn = fragment.getStartVisualColumn();
      int maxColumn = fragment.getEndVisualColumn();
      if (column > minColumn && column < maxColumn ||
          column == minColumn && pos.leansRight ||
          column == maxColumn && !pos.leansRight) {
        return new LogicalPosition(column == maxColumn ? fragment.getEndLogicalLine() : fragment.getStartLogicalLine(), 
                                   fragment.visualToLogicalColumn(column), fragment.isCollapsedFoldRegion() ? 
                                                                           column < maxColumn : 
                                                                           fragment.isRtl() ^ pos.leansRight);
      }
      maxLogicalColumn = logicalLine == fragment.getEndLogicalLine() ? Math.max(maxLogicalColumn, fragment.getMaxLogicalColumn()) : 
                         fragment.getMaxLogicalColumn();
      maxVisualColumn = maxColumn;
      logicalLine = fragment.getEndLogicalLine();
    }
    return new LogicalPosition(logicalLine, column - maxVisualColumn + maxLogicalColumn, pos.leansRight);
  }

  @NotNull
  VisualPosition offsetToVisualPosition(int offset, boolean leanTowardsLargerOffsets) {
    return logicalToVisualPosition(offsetToLogicalPosition(offset).leanForward(leanTowardsLargerOffsets));
  }

  int offsetToVisualLine(int offset) {
    int textLength = myDocument.getTextLength();
    if (offset < 0 || textLength == 0) {
      return 0;
    }
    offset = Math.min(offset, textLength);

    FoldRegion outermostCollapsed = myFoldingModel.getCollapsedRegionAtOffset(offset);
    if (outermostCollapsed != null && offset > outermostCollapsed.getStartOffset()) {
      assert outermostCollapsed.isValid();
      offset = outermostCollapsed.getStartOffset();
    }

    return myDocument.getLineNumber(offset) - myFoldingModel.getFoldedLinesCountBefore(offset);
  }

  int visualToLogicalLine(int visualLine) {
    FoldRegion[] regions = myFoldingModel.fetchTopLevel();
    if (regions == null || regions.length == 0) return visualLine;
    int start = 0;
    int end = regions.length - 1;
    int i = 0;
    while (start <= end) {
      i = (start + end) / 2;
      FoldRegion region = regions[i];
      assert region.isValid();
      int regionVisualLine = offsetToVisualLine(region.getStartOffset());
      if (regionVisualLine < visualLine) {
        start = i + 1;
      }
      else if (regionVisualLine > visualLine) {
        end = i - 1;
      }
      else {
        break;
      }
    }
    while (i >= 0) {
      FoldRegion region = regions[i];
      assert region.isValid();
      if (offsetToVisualLine(region.getStartOffset()) < visualLine) break;
      i--;
    }
    i++;
    int offset;
    if (i < regions.length) {
      FoldRegion region = regions[i];
      assert region.isValid();
      offset = region.getStartOffset();
    }
    else {
      offset = myDocument.getTextLength();
    }
    return visualLine + myFoldingModel.getFoldedLinesCountBefore(offset);
  }

  private float getStartX(int line) {
    return line == 0 ? myView.getPrefixTextWidthInPixels() : 0;
  }

  @NotNull
  VisualPosition xyToVisualPosition(@NotNull Point p) {
    int visualLine = yToVisualLine(Math.max(p.y, 0));
    int logicalLine = visualToLogicalLine(visualLine);
    int lastColumn = 0;
    float x = getStartX(logicalLine);
    if (logicalLine < myDocument.getLineCount()) {
      for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, 
                                                                                              myDocument.getLineStartOffset(logicalLine))) {
        float nextX = fragment.getEndX();
        if (p.x <= nextX) {
          int[] column = fragment.xToVisualColumn(p.x);
          return new VisualPosition(visualLine, column[0], column[1] > 0);
        }
        else {
          x = nextX;
          lastColumn = fragment.getEndVisualColumn();
        }
      }
    }
    int plainSpaceWidth = myView.getPlainSpaceWidth();
    int remainingShift = (int)(p.x - x);
    int additionalColumns = remainingShift <= 0 ? 0 : (remainingShift + plainSpaceWidth / 2) / plainSpaceWidth;
    return new VisualPosition(visualLine, lastColumn + additionalColumns, 
                              remainingShift >= 0 && additionalColumns == remainingShift / plainSpaceWidth);
  }

  @NotNull
  Point visualPositionToXY(@NotNull VisualPosition pos) {
    int visualLine = pos.line;
    int column = pos.column;
    int y = visualLineToY(visualLine);
    int logicalLine = visualToLogicalLine(visualLine);
    float x = getStartX(logicalLine);
    int lastColumn = 0;
    if (logicalLine < myDocument.getLineCount()) {
      for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, 
                                                                                              myDocument.getLineStartOffset(logicalLine))) {
        int endColumn = fragment.getEndVisualColumn();
        if (column <= endColumn) {
          return new Point((int)fragment.visualColumnToX(column), y);
        }
        else {
          x = fragment.getEndX();
          lastColumn = endColumn;
        }
      }
    }
    int additionalShift = column <= lastColumn ? 0 : (column - lastColumn) * myView.getPlainSpaceWidth();
    return new Point((int)(x) + additionalShift, y);
  }

  @NotNull
  Point offsetToXY(int offset, boolean leanTowardsLargerOffsets) {
    offset = Math.max(0, Math.min(myDocument.getTextLength(), offset));
    int logicalLine = myDocument.getLineNumber(offset);
    int lineStartOffset = myDocument.getLineStartOffset(logicalLine);
    int intraLineOffset = offset - lineStartOffset;
    int y = visualLineToY(offsetToVisualLine(offset));
    float x = getStartX(logicalLine);
    if (myDocument.getTextLength() > 0) {
      for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, offset)) {
        if (intraLineOffset == 0 && !leanTowardsLargerOffsets &&
            fragment.getStartVisualColumn() == 0 && fragment.getStartLogicalLine() == logicalLine) {
          break;
        }
        int minOffset = fragment.getMinOffset();
        int maxOffset = fragment.getMaxOffset();
        if (offset > minOffset && offset < maxOffset ||
            offset == minOffset && leanTowardsLargerOffsets ||
            offset == maxOffset && !leanTowardsLargerOffsets) {
          x = fragment.offsetToX(offset);
          break;
        }
        else {
          x = fragment.getEndX();
        }
      }
    }
    return new Point((int)x, y);
  }
}
