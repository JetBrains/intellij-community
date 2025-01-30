// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.InlayModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FoldingModelImpl;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IntPair;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.geom.Point2D;
import java.util.List;

/**
 * Performs transformations between various location representations in editor
 * (offset, logical position, visual position, pixel coordinates).
 *
 * @see LogicalPosition
 * @see VisualPosition
 */
final class EditorCoordinateMapper {
  private final EditorView myView;
  private final Document myDocument;
  private final FoldingModelImpl myFoldingModel;
  private final InlayModelEx myInlayModel;
  private final SoftWrapModelImpl mySoftWrapModel;

  EditorCoordinateMapper(EditorView view) {
    myView = view;
    myDocument = myView.getDocument();
    myFoldingModel = myView.getFoldingModel();
    myInlayModel = myView.getInlayModel();
    mySoftWrapModel = myView.getSoftWrapModel();
  }

  int[] visualLineToYRange(int line) {
    if (line < 0) line = 0;
    int offset = line >= myView.getVisibleLineCount() ? myDocument.getTextLength() + 1 : visualLineToOffset(line);
    int lineHeight = myView.getLineHeight();
    int idx = myFoldingModel.getLastCollapsedRegionBefore(offset);
    IntPair adjustment = myFoldingModel.getCustomRegionsYAdjustment(offset, idx);
    int startY = myView.getInsets().top + line * lineHeight + adjustment.first +
                 myInlayModel.getHeightOfBlockElementsBeforeVisualLine(line, offset, idx);
    return new int[] {startY, startY + lineHeight + adjustment.second};
  }

  int visualLineToY(int line) {
    return visualLineToYRange(line)[0];
  }

  int yToVisualLine(int y) {
    int lineHeight = myView.getLineHeight();
    y = Math.max(0, y - myView.getInsets().top);
    if (y < lineHeight) return 0;
    int lineMin = 0;
    int yMin = 0;
    int lineMax = myView.getVisibleLineCount() - 1;
    int yMax = visualLineToY(lineMax + 1);
    if (y >= yMax) {
      return lineMax + 1 + (y - yMax) / lineHeight;
    }
    while (lineMin < lineMax) {
      if ((yMax - yMin) == (lineMax - lineMin + 1) * lineHeight) return lineMin + (y - yMin) / lineHeight;
      int lineMid = (lineMin + lineMax) / 2;
      int[] yMidRange = visualLineToYRange(lineMid);
      if (y < yMidRange[0]) {
        int yMidMin = yMidRange[0] - getInlaysHeight(lineMid, true);
        if (y >= yMidMin) return lineMid;
        lineMax = lineMid - 1;
        yMax = yMidMin;
      }
      else {
        int yMidMax = yMidRange[1] + getInlaysHeight(lineMid, false);
        if (y < yMidMax) return lineMid;
        lineMin = lineMid + 1;
        yMin = yMidMax;
      }
    }
    return lineMin;
  }

  private int getInlaysHeight(int visualLine, boolean above) {
    return EditorUtil.getInlaysHeight(myInlayModel, visualLine, above);
  }

  @NotNull
  LogicalPosition offsetToLogicalPosition(int offset) {
    return myView.getLogicalPositionCache().offsetToLogicalPosition(offset);
  }

  int logicalPositionToOffset(@NotNull LogicalPosition pos) {
    return myView.getLogicalPositionCache().logicalPositionToOffset(pos);
  }

  @NotNull
  VisualPosition logicalToVisualPosition(@NotNull LogicalPosition pos, boolean beforeSoftWrap) {
    int line = pos.line;
    int column = pos.column;
    int logicalLineCount = Math.max(1, myDocument.getLineCount());
    if (line >= logicalLineCount) {
      return new VisualPosition(line - logicalLineCount + myView.getVisibleLineCount(), column, pos.leansForward);
    }
    int offset = logicalPositionToOffset(pos);
    int visualLine = offsetToVisualLine(offset, beforeSoftWrap);
    int maxVisualColumn = 0;
    int maxLogicalColumn = 0;
    int endLogicalLine = line;
    for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, offset, beforeSoftWrap)) {
      if (!pos.leansForward && offset == fragment.getVisualLineStartOffset()) {
        return new VisualPosition(visualLine, fragment.getStartVisualColumn());
      }
      endLogicalLine = fragment.getEndLogicalLine();
      maxVisualColumn = fragment.getEndVisualColumn();
      FoldRegion foldRegion = fragment.getCurrentFoldRegion();
      if (foldRegion != null) {
        if (foldRegion instanceof CustomFoldRegion) {
          return new VisualPosition(visualLine, 0);
        }
        int startLogicalLine = fragment.getStartLogicalLine();
        int startLogicalColumn = fragment.getStartLogicalColumn();
        int endLogicalColumn = fragment.getEndLogicalColumn();
        if ((line > startLogicalLine || line == startLogicalLine && (column > startLogicalColumn ||
                                                                     column == startLogicalColumn && pos.leansForward)) &&
            (line < endLogicalLine || line == endLogicalLine && column < endLogicalColumn)) {
          return new VisualPosition(visualLine, fragment.getStartVisualColumn(), true);
        }
        if (line == endLogicalLine && column == endLogicalColumn && !pos.leansForward) {
          return new VisualPosition(visualLine, maxVisualColumn);
        }
        maxLogicalColumn = startLogicalLine == endLogicalLine ? Math.max(maxLogicalColumn, endLogicalColumn) : endLogicalColumn;
      }
      else if (fragment.getCurrentInlay() == null) {
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
    }
    int resultColumn = maxVisualColumn + logToVisWithInlays(endLogicalLine, column - maxLogicalColumn, pos.leansForward);
    if (resultColumn < 0) resultColumn = Integer.MAX_VALUE; // guarding against overflow
    return new VisualPosition(visualLine, resultColumn, pos.leansForward);
  }

  private int logToVisWithInlays(int logLine, int remainingLogColumn, boolean leansForward) {
    if (remainingLogColumn > 1 || remainingLogColumn == 1 && leansForward) {
      remainingLogColumn += myInlayModel.getAfterLineEndElementsForLogicalLine(logLine).size();
    }
    return remainingLogColumn;
  }

  private int visToLogWithInlays(int logLine, int remainingVisColumns, boolean[] leansForward) {
    if (remainingVisColumns == 0) return 0;
    int inlayCount = myInlayModel.getAfterLineEndElementsForLogicalLine(logLine).size();
    if (inlayCount == 0) return remainingVisColumns;
    if (remainingVisColumns < inlayCount + 1) {
      leansForward[0] = false;
      return 1;
    }
    if (remainingVisColumns == inlayCount + 1) {
      leansForward[0] = true;
    }
    return remainingVisColumns - inlayCount;
  }

  @NotNull
  LogicalPosition visualToLogicalPosition(@NotNull VisualPosition pos) {
    int line = pos.line;
    int column = pos.column;
    int visualLineCount = myView.getVisibleLineCount();
    if (line >= visualLineCount) {
      return new LogicalPosition(line - visualLineCount + myDocument.getLineCount(), column, pos.leansRight);
    }
    int offset = visualLineToOffset(line);
    int logicalLine = myDocument.getLineNumber(offset);
    int maxVisualColumn = 0;
    int maxLogicalColumn = 0;
    int maxOffset = offset;
    LogicalPosition delayedResult = null;
    boolean delayedInlay = false;
    for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, offset, false)) {
      FoldRegion foldRegion = fragment.getCurrentFoldRegion();
      if (foldRegion instanceof CustomFoldRegion) {
        return new LogicalPosition(fragment.getStartLogicalLine(), fragment.getStartLogicalColumn());
      }
      int minColumn = fragment.getStartVisualColumn();
      int maxColumn = fragment.getEndVisualColumn();
      if (delayedResult != null && minColumn != maxColumn) {
        return delayedInlay ? delayedResult.leanForward(fragment.getCurrentInlay() == null) : delayedResult;
      }
      if (column < minColumn || column == minColumn && !pos.leansRight && minColumn != maxColumn) {
        return offsetToLogicalPosition(offset);
      }
      if (column > minColumn && column < maxColumn ||
          column == minColumn ||
          column == maxColumn && !pos.leansRight) {
        // for visual positions between adjacent inlays, we return same result as for visual position before the first one
        delayedInlay = fragment.getCurrentInlay() != null;
        delayedResult =
          new LogicalPosition(column == maxColumn ? fragment.getEndLogicalLine() : fragment.getStartLogicalLine(),
                              fragment.visualToLogicalColumn(column),
                              foldRegion != null ? column < maxColumn :
                              !delayedInlay && fragment.isRtl() ^ pos.leansRight);
        // delaying result to check whether there's an 'invisible' fold region going next
        if (column != maxColumn) return delayedResult;
      }
      maxLogicalColumn = logicalLine == fragment.getEndLogicalLine() ? Math.max(maxLogicalColumn, fragment.getMaxLogicalColumn()) :
                         fragment.getMaxLogicalColumn();
      maxVisualColumn = maxColumn;
      logicalLine = fragment.getEndLogicalLine();
      maxOffset = Math.max(maxOffset, fragment.getMaxOffset());
    }
    if (delayedResult != null && !delayedInlay) return delayedResult;
    if (mySoftWrapModel.getSoftWrap(maxOffset) == null) {
      boolean[] leansForward = new boolean[] {pos.leansRight};
      int resultColumn = maxLogicalColumn + visToLogWithInlays(logicalLine, column - maxVisualColumn, leansForward);
      if (resultColumn < 0) resultColumn = Integer.MAX_VALUE; // guarding against overflow
      return new LogicalPosition(logicalLine, resultColumn, leansForward[0]);
    }
    else {
      return offsetToLogicalPosition(maxOffset).leanForward(true);
    }
  }

  @NotNull
  VisualPosition offsetToVisualPosition(int offset, boolean leanTowardsLargerOffsets, boolean beforeSoftWrap) {
    return logicalToVisualPosition(offsetToLogicalPosition(offset).leanForward(leanTowardsLargerOffsets), beforeSoftWrap);
  }

  int visualPositionToOffset(VisualPosition visualPosition) {
    return logicalPositionToOffset(visualToLogicalPosition(visualPosition));
  }

  int offsetToVisualLine(int offset, boolean beforeSoftWrap) {
    int textLength = myDocument.getTextLength();
    if (offset < 0 || textLength == 0) {
      return 0;
    }
    offset = Math.min(offset, textLength);
    offset = DocumentUtil.alignToCodePointBoundary(myDocument, offset);

    FoldRegion outermostCollapsed = myFoldingModel.getCollapsedRegionAtOffset(offset);
    if (outermostCollapsed != null && offset > outermostCollapsed.getStartOffset()) {
      assert outermostCollapsed.isValid();
      offset = outermostCollapsed.getStartOffset();
      beforeSoftWrap = false;
    }

    int wrapIndex = mySoftWrapModel.getSoftWrapIndex(offset);
    int softWrapsBeforeOrAtOffset = wrapIndex < 0 ? (- wrapIndex - 1) : wrapIndex + (beforeSoftWrap ? 0 : 1);

    return myDocument.getLineNumber(offset) - myFoldingModel.getFoldedLinesCountBefore(offset) + softWrapsBeforeOrAtOffset;
  }

  int visualLineToOffset(int visualLine) {
    int start = 0;
    int end = myDocument.getTextLength();
    if (visualLine <= 0) return start;
    if (visualLine >= myView.getVisibleLineCount()) return end;
    int current = ObjectUtils.binarySearch(0, myDocument.getTextLength(), mid -> Integer.compare(offsetToVisualLine(mid, false), visualLine));
    if (current < 0) current = -current-1;
    return visualLineStartOffset(current, true);
  }

  private int visualLineStartOffset(int offset, boolean leanForward) {
    offset = DocumentUtil.alignToCodePointBoundary(myDocument, offset);
    int result = EditorUtil.getNotFoldedLineStartOffset(myDocument, myFoldingModel, offset, false);

    List<? extends SoftWrap> softWraps = mySoftWrapModel.getRegisteredSoftWraps();
    int currentOrPrevWrapIndex = mySoftWrapModel.getSoftWrapIndex(offset);
    SoftWrap currentOrPrevWrap;
    if (currentOrPrevWrapIndex < 0) {
      currentOrPrevWrapIndex = - currentOrPrevWrapIndex - 2;
      currentOrPrevWrap = currentOrPrevWrapIndex < 0 || currentOrPrevWrapIndex >= softWraps.size() ? null :
                          softWraps.get(currentOrPrevWrapIndex);
    }
    else {
      currentOrPrevWrap = leanForward ? softWraps.get(currentOrPrevWrapIndex) : null;
    }
    if (currentOrPrevWrap != null && currentOrPrevWrap.getStart() > result) {
      result = currentOrPrevWrap.getStart();
    }
    return result;
  }

  private float getStartX(int line) {
    return isRightAligned() ?
           getRightAlignmentLineStartX(line) :
           myView.getInsets().left + (line == 0 ? myView.getPrefixTextWidthInPixels() : 0);
  }

  private boolean isRightAligned() {
    return myView.getEditor().isRightAligned();
  }

  float getRightAlignmentLineStartX(int visualLine) {
    checkRightAlignment();
    int max = getRightAlignmentMarginX();
    float shift = visualLine == 0 ? myView.getPrefixTextWidthInPixels() : 0;
    if (visualLine >= myView.getVisibleLineCount()) return max - shift;
    int lineWidth = myView.getSizeManager().getVisualLineWidth(new VisualLinesIterator(myView, visualLine), false);
    return Math.max(max - lineWidth, 0);
  }

  int getRightAlignmentMarginX() {
    checkRightAlignment();
    EditorImpl editor = myView.getEditor();
    JScrollBar vsb = editor.getScrollPane().getVerticalScrollBar();
    int vsbWidth = vsb != null && editor.getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_RIGHT ? vsb.getWidth() : 0;
    return editor.getContentComponent().getWidth() - myView.getInsets().right - editor.getSettings().getLineCursorWidth() - vsbWidth;
  }

  private void checkRightAlignment() {
    if (!isRightAligned()) throw new IllegalStateException("Editor is not right-aligned");
  }

  @NotNull
  VisualPosition xyToVisualPosition(@NotNull Point2D p) {
    int visualLine = yToVisualLine((int)p.getY());
    int lastColumn = 0;
    float x = getStartX(visualLine);
    float px = (float)p.getX();
    int logicalLine = -1;
    if (visualLine < myView.getVisibleLineCount()) {
      int visualLineStartOffset = visualLineToOffset(visualLine);
      int maxOffset = 0;
      for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, visualLineStartOffset, false, true)) {
        if (fragment.getCurrentFoldRegion() instanceof CustomFoldRegion) {
          return new VisualPosition(visualLine, 0);
        }
        if (px <= fragment.getStartX()) {
          if (fragment.getStartVisualColumn() == 0) {
            return new VisualPosition(visualLine, 0);
          }
          int markerWidth = mySoftWrapModel.getMinDrawingWidthInPixels(SoftWrapDrawingType.AFTER_SOFT_WRAP);
          float indent = fragment.getStartX() - markerWidth;
          if (px <= indent) {
            break;
          }
          boolean after = px >= indent + markerWidth / 2;
          return new VisualPosition(visualLine, fragment.getStartVisualColumn() - (after ? 0 : 1), !after);
        }
        float nextX = fragment.getEndX();
        if (px <= nextX) {
          int[] column = fragment.xToVisualColumn(px);
          return new VisualPosition(visualLine, column[0], column[1] > 0);
        }
        x = nextX;
        lastColumn = fragment.getEndVisualColumn();
        maxOffset = Math.max(maxOffset, fragment.getMaxOffset());
        logicalLine = fragment.getEndLogicalLine();
      }
      if (mySoftWrapModel.getSoftWrap(maxOffset) != null) {
        int markerWidth = mySoftWrapModel.getMinDrawingWidthInPixels(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED);
        if (px <= x + markerWidth) {
          boolean after = px >= x + markerWidth / 2;
          return new VisualPosition(visualLine, lastColumn + (after ? 1 : 0), !after);
        }
        px -= markerWidth;
        lastColumn++;
        logicalLine = -1;
      }
      else if (logicalLine == -1) {
        logicalLine = myDocument.getLineNumber(visualLineStartOffset);
      }
    }
    float plainSpaceWidth = myView.getPlainSpaceWidth();
    float remainingShift = px - x;
    if (remainingShift > plainSpaceWidth && logicalLine >= 0) {
      List<Inlay<?>> inlays = myInlayModel.getAfterLineEndElementsForLogicalLine(logicalLine);
      int inlaysWidth = 0;
      int inlayCount = 0;
      for (Inlay inlay : inlays) {
        int width = inlay.getWidthInPixels();
        int newWidth = inlaysWidth + width;
        if (remainingShift <= plainSpaceWidth + newWidth) {
          boolean leftPart = remainingShift <= plainSpaceWidth + (inlaysWidth + newWidth) / 2;
          return new VisualPosition(visualLine, lastColumn + 1 + inlayCount + (leftPart ? 0 : 1), leftPart);
        }
        inlaysWidth = newWidth;
        inlayCount++;
      }
      remainingShift -= inlaysWidth;
      lastColumn += inlayCount;
    }
    int additionalColumns = remainingShift <= 0 ? 0 : Math.round(remainingShift / plainSpaceWidth);
    return new VisualPosition(visualLine, lastColumn + additionalColumns, remainingShift > additionalColumns * plainSpaceWidth);
  }

  @NotNull
  Point2D visualPositionToXY(@NotNull VisualPosition pos) {
    int visualLine = pos.line;
    int column = pos.column;
    int y = visualLineToY(visualLine);
    float x = getStartX(visualLine);
    int lastColumn = 0;
    int logicalLine = -1;
    if (visualLine < myView.getVisibleLineCount()) {
      int visualLineStartOffset = visualLineToOffset(visualLine);
      int maxOffset = 0;
      for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, visualLineStartOffset, false, true)) {
        if (fragment.getCurrentFoldRegion() instanceof CustomFoldRegion) {
          return new Point2D.Double(fragment.getStartX(), y);
        }
        int startVisualColumn = fragment.getStartVisualColumn();
        if (column < startVisualColumn || column == startVisualColumn && !pos.leansRight) {
          break;
        }
        int endColumn = fragment.getEndVisualColumn();
        if (column < endColumn || column == endColumn && !pos.leansRight) {
          return new Point2D.Double(fragment.visualColumnToX(column), y);
        }
        x = fragment.getEndX();
        lastColumn = endColumn;
        maxOffset = Math.max(maxOffset, fragment.getMaxOffset());
        logicalLine = fragment.getEndLogicalLine();
      }
      if (column > lastColumn && mySoftWrapModel.getSoftWrap(maxOffset) != null) {
        column--;
        x += mySoftWrapModel.getMinDrawingWidthInPixels(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED);
      }
      else if (logicalLine == -1) {
        logicalLine = myDocument.getLineNumber(visualLineStartOffset);
      }
    }
    if (column > lastColumn + 1 && logicalLine >= 0) {
      List<Inlay<?>> inlays = myInlayModel.getAfterLineEndElementsForLogicalLine(logicalLine);
      int inlaysWidth = 0;
      int inlayCount = 0;
      for (Inlay inlay : inlays) {
        inlayCount++;
        inlaysWidth += inlay.getWidthInPixels();
        if (column == lastColumn + 1 + inlayCount) {
          break;
        }
      }
      x += inlaysWidth;
      column -= inlayCount;
    }
    float additionalShift = column <= lastColumn ? 0 : (column - lastColumn) * myView.getPlainSpaceWidth();
    return new Point2D.Double(x + additionalShift, y);
  }

  @NotNull
  Point2D offsetToXY(int offset, boolean leanTowardsLargerOffsets, boolean beforeSoftWrap) {
    offset = Math.max(0, Math.min(myDocument.getTextLength(), offset));
    offset = DocumentUtil.alignToCodePointBoundary(myDocument, offset);
    int logicalLine = myDocument.getLineNumber(offset);
    int visualLine = offsetToVisualLine(offset, beforeSoftWrap);
    int visualLineStartOffset = visualLineToOffset(visualLine);
    int y = visualLineToY(visualLine);
    float x = getStartX(logicalLine);
    boolean firstFragment = true;
    for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, offset, beforeSoftWrap, true)) {
      if (firstFragment && offset == visualLineStartOffset && !leanTowardsLargerOffsets ||
          fragment.getCurrentFoldRegion() instanceof CustomFoldRegion) {
        x = fragment.getStartX();
        break;
      }
      firstFragment = false;
      int minOffset = fragment.getMinOffset();
      int maxOffset = fragment.getMaxOffset();
      if (fragment.getCurrentInlay() == null &&
          (offset > minOffset && offset < maxOffset ||
          offset == minOffset && leanTowardsLargerOffsets ||
          offset == maxOffset && !leanTowardsLargerOffsets)) {
        x = fragment.offsetToX(offset);
        break;
      }
      else {
        x = fragment.getEndX();
      }
    }
    return new Point2D.Double(x, y);
  }
}
