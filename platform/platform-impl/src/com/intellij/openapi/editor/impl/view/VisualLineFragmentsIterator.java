// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.DocumentText;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.InlayModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.openapi.editor.impl.softwrap.CustomWrapToSoftWrapAdapter;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapEx;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Graphics2D;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

/**
 * Iterator over visual line's fragments. Fragment's text has the same font and directionality. Collapsed fold regions are also represented
 * as fragments.
 */
final class VisualLineFragmentsIterator implements Iterator<VisualLineFragmentsIterator.Fragment> {
  static @NotNull Iterable<Fragment> create(
    @NotNull EditorView view,
    @NotNull DocumentText document,
    int offset,
    boolean beforeSoftWrap,
    boolean align
  ) {
    return () -> new VisualLineFragmentsIterator(view, document, offset, beforeSoftWrap, align);
  }

  /**
   * If {@code quickEvaluationListener} is provided, quick approximate iteration mode becomes enabled, listener will be invoked
   * if approximation will in fact be used during width calculation.
   */
  static @NotNull Iterable<Fragment> create(
    @NotNull EditorView view,
    @NotNull DocumentText document,
    @NotNull VisualLinesIterator visualLinesIterator,
    @Nullable Runnable quickEvaluationListener,
    boolean align
  ) {
    return () -> new VisualLineFragmentsIterator(view, document, visualLinesIterator, quickEvaluationListener, align);
  }

  private final EditorView myView;
  private final DocumentText myDocument;
  private final InlayModelEx myInlayModel;
  private final ScaleContext myScaleContext;
  private final TextLayoutCache myTextLayoutCache;
  private final FoldRegion[] myRegions;
  private final SoftWrapEx myNextWrap;
  private final int myVisualLineStartOffset;
  /**
   * The x shift of a right-aligned visual line, or 0.
   * The x coordinates of a {@link Fragment} include it. The x coordinates of a {@link LineVisualFragment} do not.
   */
  private final float myXOffset;
  private final Fragment myFragment;
  private final Runnable myQuickEvaluationListener;

  private int mySegmentStartOffset;
  private int mySegmentEndOffset;
  private float myCurrentX;
  private float myPrevX;
  private int myCurrentVisualColumn;
  private int myCurrentStartLogicalLine;
  private int myCurrentStartLogicalLineStart;
  private int myCurrentEndLogicalLine;
  private Iterator<LineVisualFragment> myFragmentIterator;
  private LineVisualFragment myDelegate;
  private List<Inlay<?>> myInlays;
  private int myCurrentInlayIndex;
  private FoldRegion myFoldRegion;
  private int myCurrentFoldRegionIndex;
  private int myFoldRegionColumns;

  private VisualLineFragmentsIterator(
    @NotNull EditorView view,
    @NotNull DocumentText document,
    int offset,
    boolean beforeSoftWrap,
    boolean align
  ) {
    FoldingModelEx foldingModel = view.getFoldingModel();
    SoftWrapModelImpl softWrapModel = view.getSoftWrapModel();
    int startOrPrevWrapIndex = calculateStartOrPrevWrapIndex(softWrapModel, offset, beforeSoftWrap);
    int visualLineStartOffset = calculateVisualLineStartOffset(
      document,
      foldingModel,
      softWrapModel,
      offset,
      startOrPrevWrapIndex
    );
    int startFoldingIndex = foldingModel.getLastCollapsedRegionBefore(visualLineStartOffset) + 1;
    int visualLine = align ? view.offsetToVisualPosition(offset, false, false).line : -1;
    int startLogicalLine = document.lineNumber(visualLineStartOffset);
    this(
      view,
      document,
      visualLine,
      visualLineStartOffset,
      startLogicalLine,
      startOrPrevWrapIndex,
      startFoldingIndex,
      null,
      align
    );
  }

  private VisualLineFragmentsIterator(
    @NotNull EditorView view,
    @NotNull DocumentText document,
    @NotNull VisualLinesIterator visualLinesIterator,
    @Nullable Runnable quickEvaluationListener,
    boolean align
  ) {
    assert !visualLinesIterator.atEnd();
    this(
      view,
      document,
      visualLinesIterator.getVisualLine(),
      visualLinesIterator.getVisualLineStartOffset(),
      visualLinesIterator.getStartLogicalLine(),
      visualLinesIterator.getStartOrPrevWrapIndex(),
      visualLinesIterator.getStartFoldingIndex(),
      quickEvaluationListener,
      align
    );
  }

  private VisualLineFragmentsIterator(
    @NotNull EditorView view,
    @NotNull DocumentText document,
    int visualLine,
    int visualLineStartOffset,
    int startLogicalLine,
    int startOrPrevWrapIndex,
    int startFoldingIndex,
    @Nullable Runnable quickEvaluationListener,
    boolean align
  ) {
    myView = view;
    myDocument = document;
    myInlayModel = view.getInlayModel();
    myTextLayoutCache = view.getTextLayoutCache();
    myQuickEvaluationListener = quickEvaluationListener;
    myScaleContext = ScaleContext.create(view.getEditor().getContentComponent());
    myXOffset = calculateXOffset(view, visualLine, align);
    myFragment = new Fragment();
    myRegions = fetchTopLevelFoldings(view);
    myVisualLineStartOffset = visualLineStartOffset;
    var softWraps = view.getSoftWrapModel().getRegisteredSoftWrapsEx();
    myNextWrap = getSoftWrap(softWraps, startOrPrevWrapIndex + 1);
    init(visualLineStartOffset, startLogicalLine, startFoldingIndex, getSoftWrap(softWraps, startOrPrevWrapIndex));
  }

  @Override
  public boolean hasNext() {
    if (myFoldRegion instanceof CustomFoldRegion) {
      return false;
    }
    if (mySegmentStartOffset == getCurrentFoldRegionStartOffset() ||
        myFragmentIterator == null ||
        myFragmentIterator.hasNext()) {
      return true;
    }
    return false;
  }

  @Override
  public Fragment next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    float prevX = myCurrentX;
    int startLogicalLine = myCurrentEndLogicalLine;
    int startLogicalLineStart = myDocument.lineStartOffset(startLogicalLine);
    if (mySegmentStartOffset == getCurrentFoldRegionStartOffset()) {
      moveToFoldRegion();
    } else if (myFragmentIterator == null) {
      moveToInlay();
    } else {
      moveToLineFragment();
    }
    myPrevX = prevX;
    myCurrentStartLogicalLine = startLogicalLine;
    myCurrentStartLogicalLineStart = startLogicalLineStart;
    return myFragment;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  private void moveToFoldRegion() {
    FoldRegion foldRegion = myRegions[myCurrentFoldRegionIndex];
    assert foldRegion.isValid();
    int foldRegionColumns = getFoldRegionWidthInColumns(foldRegion);
    float foldRegionWidth = getFoldRegionWidthInPixels(foldRegion);
    int segmentStartOffset = foldRegion.getEndOffset();
    enterSegment(
      segmentStartOffset,
      myDocument.lineNumber(segmentStartOffset),
      myCurrentFoldRegionIndex + 1,
      myCurrentVisualColumn + foldRegionColumns,
      myCurrentX + foldRegionWidth,
      null
    );
    myDelegate = null;
    myFoldRegion = foldRegion;
    myFoldRegionColumns = foldRegionColumns;
  }

  private void moveToInlay() {
    Inlay<?> inlay = myInlays.get(myCurrentInlayIndex);
    float x = (float)(myCurrentX + alignToInt(inlay.getWidthInPixels()));
    int visualColumn = myCurrentVisualColumn + 1;
    int inlayIndex = myCurrentInlayIndex + 1;
    Iterator<LineVisualFragment> fragmentIterator = null;
    if (inlayIndex >= myInlays.size() ||
        myInlays.get(inlayIndex).getOffset() > inlay.getOffset()) {
      fragmentIterator = createFragmentIterator(
        mySegmentStartOffset,
        mySegmentEndOffset,
        myCurrentEndLogicalLine,
        myCurrentFoldRegionIndex,
        myInlays,
        inlayIndex,
        visualColumn,
        x
      );
    }
    myDelegate = null;
    myFoldRegion = null;
    myCurrentX = x;
    myCurrentVisualColumn = visualColumn;
    myCurrentInlayIndex = inlayIndex;
    myFragmentIterator = fragmentIterator;
  }

  private void moveToLineFragment() {
    LineVisualFragment delegate = myFragmentIterator.next();
    float x = delegate.getEndX();
    int visualColumn = delegate.getEndVisualColumn();
    Iterator<LineVisualFragment> fragmentIterator = myFragmentIterator;
    int segmentStartOffset = mySegmentStartOffset;
    if (!myFragmentIterator.hasNext()) {
      if (myCurrentInlayIndex < myInlays.size()) {
        fragmentIterator = null;
      } else {
        segmentStartOffset = mySegmentEndOffset;
      }
    }
    myDelegate = delegate;
    myFoldRegion = null;
    myCurrentX = x;
    myCurrentVisualColumn = visualColumn;
    myFragmentIterator = fragmentIterator;
    mySegmentStartOffset = segmentStartOffset;
  }

  private void init(
    int visualLineStartOffset,
    int startLogicalLine,
    int startFoldingIndex,
    @Nullable SoftWrapEx startOrPrevWrap
  ) {
    float startX = calculateStartX(visualLineStartOffset, startOrPrevWrap);
    int startVisualColumn = calculateStartVisualColumn(visualLineStartOffset, startOrPrevWrap);
    enterSegment(
      visualLineStartOffset,
      startLogicalLine,
      startFoldingIndex,
      startVisualColumn,
      startX,
      startOrPrevWrap
    );
  }

  private float calculateStartX(int visualLineStartOffset, @Nullable SoftWrapEx startOrPrevWrap) {
    int leftInset = myView.getInsets().left;
    if (visualLineStartOffset == 0) {
      return leftInset + myView.getPrefixTextWidthInPixels();
    }
    if (startOrPrevWrap != null && visualLineStartOffset == startOrPrevWrap.getStart()) {
      return (float)(leftInset + alignToInt(startOrPrevWrap.getIndentInPixels()));
    }
    return leftInset;
  }

  private void enterSegment(
    int segmentStartOffset,
    int logicalLine,
    int foldRegionIndex,
    int visualColumn,
    float x,
    @Nullable SoftWrapEx startWrap
  ) {
    int foldRegionStartOffset = getFoldRegionStartOffset(foldRegionIndex);
    if (foldRegionStartOffset < segmentStartOffset) {
      String message = assertMessage(segmentStartOffset, foldRegionStartOffset, foldRegionIndex, null, 0);
      myView.getEditor().throwEditorInvariantBroken(message);
    }
    int segmentEndOffset = foldRegionStartOffset;
    List<Inlay<?>> inlays = List.of();
    Iterator<LineVisualFragment> fragmentIterator = null;
    if (segmentStartOffset < foldRegionStartOffset) {
      segmentEndOffset = calculateSegmentEndOffset(logicalLine, foldRegionStartOffset);
      inlays = calculateInlays(segmentStartOffset, segmentEndOffset, foldRegionStartOffset, startWrap);
      if (inlays.isEmpty() || inlays.getFirst().getOffset() > segmentStartOffset) {
        fragmentIterator = createFragmentIterator(
          segmentStartOffset,
          segmentEndOffset,
          logicalLine,
          foldRegionIndex,
          inlays,
          0,
          visualColumn,
          x
        );
      }
    }
    mySegmentStartOffset = segmentStartOffset;
    mySegmentEndOffset = segmentEndOffset;
    myCurrentEndLogicalLine = logicalLine;
    myCurrentFoldRegionIndex = foldRegionIndex;
    myCurrentVisualColumn = visualColumn;
    myCurrentX = x;
    myInlays = inlays;
    myCurrentInlayIndex = 0;
    myFragmentIterator = fragmentIterator;
  }

  private int calculateSegmentEndOffset(int logicalLine, int foldRegionStartOffset) {
    int lineEndOffset = myDocument.lineEndOffset(logicalLine);
    return Math.min(getNextWrapOffset(), Math.min(foldRegionStartOffset, lineEndOffset));
  }

  private @NotNull List<Inlay<?>> calculateInlays(
    int segmentStartOffset,
    int segmentEndOffset,
    int foldRegionStartOffset,
    @Nullable SoftWrapEx startWrap
  ) {
    boolean normalLineEnd = segmentEndOffset < foldRegionStartOffset &&
                            (segmentEndOffset < getNextWrapOffset() || myNextWrap instanceof CustomWrapToSoftWrapAdapter);
    int effectiveEndOffset = normalLineEnd ? segmentEndOffset : segmentEndOffset - 1;
    int endOffsetForInlays = Math.max(segmentStartOffset, effectiveEndOffset);
    // including inlays at line end
    List<Inlay<?>> inlays = myInlayModel.getInlineElementsInRange(segmentStartOffset, endOffsetForInlays);
    if (myNextWrap instanceof CustomWrapToSoftWrapAdapter customWrap &&
        customWrap.getStart() == segmentEndOffset) {
      for (int i = inlays.size() - 1; i >= 0; i--) {
        Inlay<?> inlay = inlays.get(i);
        if (inlay.getOffset() < segmentEndOffset) {
          break;
        }
        if (!inlay.isRelatedToPrecedingText()) {
          inlays.remove(i);
        }
      }
    }
    if (startWrap instanceof CustomWrapToSoftWrapAdapter customWrap &&
        customWrap.getStart() == segmentStartOffset) {
      for (var it = inlays.iterator(); it.hasNext();) {
        Inlay<?> inlay = it.next();
        if (inlay.getOffset() > segmentStartOffset) {
          break;
        }
        if (inlay.isRelatedToPrecedingText()) {
          it.remove();
        }
      }
    }
    return inlays;
  }

  private @NotNull Iterator<LineVisualFragment> createFragmentIterator(
    int segmentStartOffset,
    int segmentEndOffset,
    int logicalLine,
    int foldRegionIndex,
    @NotNull List<Inlay<?>> inlays,
    int inlayIndex,
    int visualColumn,
    float x
  ) {
    if (logicalLine >= myDocument.lineCount()) {
      // handle empty document case
      return Collections.emptyIterator();
    }
    int startOffset = inlayIndex > 0 ? inlays.get(inlayIndex - 1).getOffset() : segmentStartOffset;
    int endOffset = inlayIndex < inlays.size() ? inlays.get(inlayIndex).getOffset() : segmentEndOffset;
    if (startOffset > endOffset) {
      String message = assertMessage(segmentStartOffset, segmentEndOffset, foldRegionIndex, inlays, inlayIndex);
      myView.getEditor().throwEditorInvariantBroken(message);
    }
    int lineStartOffset = myDocument.lineStartOffset(logicalLine);
    LineLayout lineLayout = myTextLayoutCache.getLineLayout(myDocument, logicalLine);
    return lineLayout.getFragmentsInVisualOrder(
      myView,
      myDocument,
      logicalLine,
      startOffset - lineStartOffset,
      endOffset - lineStartOffset,
      visualColumn,
      x,
      myQuickEvaluationListener
    );
  }

  private int getCurrentFoldRegionStartOffset() {
    return getFoldRegionStartOffset(myCurrentFoldRegionIndex);
  }

  private int getFoldRegionStartOffset(int foldRegionIndex) {
    if (foldRegionIndex >= myRegions.length) {
      return Integer.MAX_VALUE;
    }
    int nextFoldingOffset = myRegions[foldRegionIndex].getStartOffset();
    if (nextFoldingOffset < getNextWrapOffset()) {
      return nextFoldingOffset;
    }
    return Integer.MAX_VALUE;
  }

  private float getFoldRegionWidthInPixels(@NotNull FoldRegion foldRegion) {
    if (foldRegion instanceof CustomFoldRegion region) {
      return region.getWidthInPixels();
    }
    return myView.getFoldRegionLayout(foldRegion).getWidth();
  }

  private int getFoldRegionWidthInColumns(@NotNull FoldRegion foldRegion) {
    int maxVisualColumn = 0;
    for (var fragment : myView.getFoldRegionLayout(foldRegion).getFragmentsInVisualOrder(0)) {
      maxVisualColumn = fragment.getEndVisualColumn();
    }
    return maxVisualColumn;
  }

  private @NotNull VisualColumn getVisualColumnForXInsideFoldRegion(float x) {
    LineLayout layout = getFoldRegionLayout();
    for (var fragment : layout.getFragmentsInVisualOrder(0)) {
      if (x <= fragment.getEndX()) {
        return fragment.xToVisualColumn(x);
      }
    }
    return new VisualColumn(myFoldRegionColumns, true);
  }

  private float getXForVisualColumnInsideFoldRegion(int column) {
    LineLayout layout = getFoldRegionLayout();
    for (LineVisualFragment fragment : layout.getFragmentsInVisualOrder(0)) {
      if (column <= fragment.getEndVisualColumn()) {
        return fragment.visualColumnToX(column);
      }
    }
    return getFoldRegionWidthInPixels(myFoldRegion);
  }

  // offset is absolute
  private float getXForOffsetInsideFoldRegion(int offset) {
    return offset < myFoldRegion.getEndOffset() ? 0 : getFoldRegionWidthInPixels(myFoldRegion);
  }

  private @NotNull LineLayout getFoldRegionLayout() {
    return myView.getFoldRegionLayout(myFoldRegion);
  }

  private @NotNull LogicalPosition offsetToLogicalPosition(int offset) {
    return myView.offsetToLogicalPosition(offset);
  }

  private double alignToInt(int width) {
    return PaintUtil.alignToInt(width, myScaleContext);
  }

  private int getNextWrapOffset() {
    return myNextWrap == null ? Integer.MAX_VALUE : myNextWrap.getStart();
  }

  private static int calculateStartOrPrevWrapIndex(
    @NotNull SoftWrapModelImpl softWrapModel,
    int offset,
    boolean beforeSoftWrap
  ) {
    int wrapIndex = softWrapModel.getSoftWrapIndex(offset);
    if (wrapIndex < 0) {
      return -wrapIndex - 2;
    }
    if (beforeSoftWrap) {
      return wrapIndex - 1;
    }
    return wrapIndex;
  }

  private static int calculateVisualLineStartOffset(
    @NotNull DocumentText document,
    @NotNull FoldingModelEx foldingModel,
    @NotNull SoftWrapModelImpl softWrapModel,
    int offset,
    int startOrPrevWrapIndex
  ) {
    int notFoldedLineStartOffset = EditorUtil.getNotFoldedLineStartOffset(document, foldingModel, offset, false);
    SoftWrapEx startOrPrevWrap = getSoftWrap(softWrapModel.getRegisteredSoftWrapsEx(), startOrPrevWrapIndex);
    if (startOrPrevWrap != null && startOrPrevWrap.getStart() > notFoldedLineStartOffset) {
      return startOrPrevWrap.getStart();
    }
    return notFoldedLineStartOffset;
  }

  private static int calculateStartVisualColumn(int visualLineStartOffset, @Nullable SoftWrapEx startOrPrevWrap) {
    if (visualLineStartOffset != 0 &&
        startOrPrevWrap != null &&
        visualLineStartOffset == startOrPrevWrap.getStart()) {
      return startOrPrevWrap.getIndentInColumns();
    }
    return 0;
  }

  private static float calculateXOffset(@NotNull EditorView view, int visualLine, boolean align) {
    if (align && visualLine != -1 && view.getEditor().isRightAligned()) {
      return view.getRightAlignmentLineStartX(visualLine) - view.getInsets().left;
    }
    return 0;
  }

  private static @Nullable SoftWrapEx getSoftWrap(@NotNull List<? extends SoftWrapEx> softWraps, int index) {
    if (0 <= index && index < softWraps.size()) {
      return softWraps.get(index);
    }
    return null;
  }

  private static @NotNull FoldRegion @NotNull [] fetchTopLevelFoldings(@NotNull EditorView view) {
    FoldingModelEx foldingModel = view.getFoldingModel();
    return ObjectUtils.notNull(foldingModel.fetchTopLevel(), FoldRegion.EMPTY_ARRAY);
  }

  final class Fragment {
    int getVisualLineStartOffset() {
      return myVisualLineStartOffset;
    }

    int getMinLogicalColumn() {
      if (myDelegate == null) {
        return offsetToLogicalPosition(getMinOffset()).column;
      }
      return myDelegate.getMinLogicalColumn();
    }

    int getMaxLogicalColumn() {
      if (myDelegate == null) {
        return offsetToLogicalPosition(getMaxOffset()).column;
      }
      return myDelegate.getMaxLogicalColumn();
    }

    int getStartLogicalColumn() {
      if (myDelegate == null) {
        return offsetToLogicalPosition(getStartOffset()).column;
      }
      return myDelegate.getStartLogicalColumn();
    }

    int getEndLogicalColumn() {
      if (myDelegate == null) {
        return offsetToLogicalPosition(getEndOffset()).column;
      }
      return myDelegate.getEndLogicalColumn();
    }

    int getStartVisualColumn() {
      if (myDelegate != null) {
        return myDelegate.getStartVisualColumn();
      }
      if (myFoldRegion != null) {
        return myCurrentVisualColumn - myFoldRegionColumns;
      }
      return myCurrentVisualColumn - 1;
    }

    int getEndVisualColumn() {
      return myCurrentVisualColumn;
    }

    int getStartLogicalLine() {
      return myCurrentStartLogicalLine;
    }

    int getEndLogicalLine() {
      return myCurrentEndLogicalLine;
    }

    float getStartX() {
      if (myDelegate != null) {
        return myDelegate.getStartX() + myXOffset;
      }
      return myPrevX + myXOffset;
    }

    float getEndX() {
      return myCurrentX + myXOffset;
    }

    // column is expected to be between minLogicalColumn and maxLogicalColumn for this fragment
    int logicalToVisualColumn(int column) {
      if (myDelegate != null) {
        return myDelegate.logicalToVisualColumn(column);
      }
      if (myFoldRegion != null) {
        return myCurrentVisualColumn - myFoldRegionColumns;
      }
      return getEndVisualColumn();
    }

    // column is expected to be between startVisualColumn and endVisualColumn for this fragment
    int visualToLogicalColumn(int column) {
      if (myDelegate != null) {
        return myDelegate.visualToLogicalColumn(column);
      }
      if (myFoldRegion != null) {
        if (column == myCurrentVisualColumn) {
          return getEndLogicalColumn();
        }
        return getStartLogicalColumn();
      }
      return getEndLogicalColumn();
    }

    VisualColumn xToVisualColumn(float x) {
      if (myDelegate != null) {
        return myDelegate.xToVisualColumn(x - myXOffset);
      } else if (myFoldRegion != null) {
        VisualColumn column = getVisualColumnForXInsideFoldRegion(x - getStartX());
        column.column += getStartVisualColumn();
        return column;
      } else {
        boolean closerToStart = ((getStartX() + getEndX()) / 2) > x;
        return new VisualColumn(myCurrentVisualColumn - (closerToStart ? 1 : 0), !closerToStart);
      }
    }

    float visualColumnToX(int column) {
      if (myDelegate != null) {
        return myDelegate.visualColumnToX(column) + myXOffset;
      }
      if (myFoldRegion != null) {
        return getStartX() + getXForVisualColumnInsideFoldRegion(column - myCurrentVisualColumn + myFoldRegionColumns);
      }
      if (column == myCurrentVisualColumn) {
        return getEndX();
      }
      return getStartX();
    }

    int getVisualLength() {
      if (myDelegate != null) {
        return myDelegate.getLength();
      }
      if (myFoldRegion != null) {
        int length = 0;
        for (var fragment : getFoldRegionLayout().getFragmentsInVisualOrder(0)) {
          length += fragment.getLength();
        }
        return length;
      }
      return 0;
    }

    // returned offset is visual and relative (counted from fragment's start)
    int visualColumnToOffset(int relativeVisualColumn) {
      if (myDelegate != null) {
        return myDelegate.visualColumnToOffset(relativeVisualColumn);
      }
      if (myFoldRegion != null) {
        int relativeOffset = 0;
        for (var fragment : getFoldRegionLayout().getFragmentsInVisualOrder(0)) {
          if (relativeVisualColumn >= fragment.getStartVisualColumn() &&
              relativeVisualColumn <= fragment.getEndVisualColumn()) {
            int visualOffset = fragment.visualColumnToOffset(relativeVisualColumn - fragment.getStartVisualColumn());
            return visualOffset + relativeOffset;
          }
          relativeOffset += fragment.getLength();
        }
      }
      return 0;
    }

    // absolute
    int getStartOffset() {
      if (myDelegate != null) {
        return myDelegate.getStartOffset() + myCurrentStartLogicalLineStart;
      }
      if (myFoldRegion != null) {
        return myFoldRegion.getStartOffset();
      }
      return getCurrentInlay().getOffset();
    }

    // absolute
    int getEndOffset() {
      if (myDelegate != null) {
        return myDelegate.getEndOffset() + myCurrentStartLogicalLineStart;
      }
      if (myFoldRegion != null) {
        return myFoldRegion.getEndOffset();
      }
      return getCurrentInlay().getOffset();
    }

    // absolute
    int getMinOffset() {
      if (myDelegate != null) {
        return myDelegate.getMinOffset() + myCurrentStartLogicalLineStart;
      }
      if (myFoldRegion != null) {
        return myFoldRegion.getStartOffset();
      }
      return getCurrentInlay().getOffset();
    }

    // absolute
    int getMaxOffset() {
      if (myDelegate != null) {
        return myDelegate.getMaxOffset() + myCurrentStartLogicalLineStart;
      }
      if (myFoldRegion != null) {
        return myFoldRegion.getEndOffset();
      }
      return getCurrentInlay().getOffset();
    }

    // offset is absolute
    float offsetToX(int offset) {
      if (myDelegate != null) {
        return myDelegate.offsetToX(offset - myCurrentStartLogicalLineStart) + myXOffset;
      }
      if (myFoldRegion != null) {
        return getStartX() + getXForOffsetInsideFoldRegion(offset);
      }
      return getEndX();
    }

    // offsets are absolute
    float offsetToX(float startX, int startOffset, int offset) {
      assert myDelegate != null;
      int lineStartOffset = myCurrentStartLogicalLineStart;
      float x = myDelegate.offsetToX(startX - myXOffset, startOffset - lineStartOffset, offset - lineStartOffset);
      return x + myXOffset;
    }

    boolean isRtl() {
      return myDelegate != null && myDelegate.isRtl();
    }

    FoldRegion getCurrentFoldRegion() {
      return myFoldRegion;
    }

    Inlay<?> getCurrentInlay() {
      if (myDelegate != null || myFoldRegion != null) {
        return null;
      }
      return myInlays.get(myCurrentInlayIndex - 1);
    }

    // offsets are visual (relative to fragment's start)
    @NotNull
    Consumer<Graphics2D> draw(float x, float y, int startRelativeOffset, int endRelativeOffset) {
      if (myDelegate != null) {
        return myDelegate.draw(x, y, startRelativeOffset, endRelativeOffset);
      }
      else if (myFoldRegion != null) {
        LineLayout foldRegionLayout = getFoldRegionLayout();
        return g -> {
          int relativeOffset = 0;
          for (var fragment : foldRegionLayout.getFragmentsInVisualOrder(x)) {
            int relativeOffsetEnd = relativeOffset + fragment.getLength();
            if (relativeOffset < endRelativeOffset && relativeOffsetEnd > startRelativeOffset) {
              fragment.draw(
                fragment.getStartX(), y,
                Math.max(0, startRelativeOffset - relativeOffset),
                Math.min(relativeOffsetEnd, endRelativeOffset) - relativeOffset
              ).accept(g);
            }
            relativeOffset = relativeOffsetEnd;
          }
        };
      }
      return _ -> {};
    }
  }

  private @NotNull String assertMessage(
    int segmentStartOffset,
    int segmentEndOffset,
    int foldRegionIndex,
    @Nullable List<Inlay<?>> inlays,
    int inlayIndex
  ) {
    String foldRegion = foldRegionIndex < myRegions.length ? myRegions[foldRegionIndex].toString() : "";
    String inlayCount = inlays != null ? String.valueOf(inlays.size()) : "null";
    String inlay = inlays != null && inlayIndex < inlays.size() ? inlays.get(inlayIndex).toString() : "";
    return String.join(
      ", ",
      "startOffset: " + segmentStartOffset,
      "endOffset: " + segmentEndOffset,
      "foldIndex: " + foldRegionIndex,
      "foldCount: " + myRegions.length,
      "fold" + foldRegion,
      "inlayIndex: " + inlayIndex,
      "inlayCount: " + inlayCount,
      "inlay: " + inlay,
      "textLength: " + myDocument.length()
    );
  }
}
