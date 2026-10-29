// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.ex.DocumentText;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.InlayModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.openapi.editor.impl.softwrap.CustomWrapToSoftWrapAdapter;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapEx;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.scale.ScaleContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Graphics2D;
import java.awt.Insets;
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
    int offset
  ) {
    return create(view, document, offset, false, false);
  }

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
  private final SoftWrapModelImpl mySoftWrapModel;
  private final FoldingModelEx myFoldingModel;
  private final InlayModelEx myInlayModel;
  private FoldRegion[] myRegions;
  private Fragment myFragment = new Fragment();
  private int myVisualLineStartOffset;
  private Runnable myQuickEvaluationListener;

  private int mySegmentStartOffset;
  private int mySegmentEndOffset;
  private int myCurrentFoldRegionIndex;
  private Iterator<LineVisualFragment> myFragmentIterator;
  private List<Inlay<?>> myInlays;
  private int myCurrentInlayIndex;
  private float myCurrentX;
  private float myPrevX;
  private int myCurrentVisualColumn;
  private LineVisualFragment myDelegate;
  private FoldRegion myFoldRegion;
  private int myFoldRegionColumns;
  private int myCurrentStartLogicalLine;
  private int myCurrentStartLogicalLineStart;
  private int myCurrentEndLogicalLine;
  private SoftWrapEx myNextWrap;
  private ScaleContext myScaleContext;

  private VisualLineFragmentsIterator(
    @NotNull EditorView view,
    @NotNull DocumentText document,
    int offset,
    boolean beforeSoftWrap,
    boolean align
  ) {
    myView = view;
    myDocument = document;
    myFoldingModel = view.getFoldingModel();
    mySoftWrapModel = view.getSoftWrapModel();
    myInlayModel = view.getInlayModel();
    int visualLineStartOffset = EditorUtil.getNotFoldedLineStartOffset(myDocument, myFoldingModel, offset, false);
    var softWraps = mySoftWrapModel.getRegisteredSoftWraps();
    int currentOrPrevWrapIndex = mySoftWrapModel.getSoftWrapIndex(offset);
    if (currentOrPrevWrapIndex < 0) {
      currentOrPrevWrapIndex = - currentOrPrevWrapIndex - 2;
    } else if (beforeSoftWrap) {
      currentOrPrevWrapIndex--;
    }
    SoftWrap currentOrPrevWrap = currentOrPrevWrapIndex < 0 || currentOrPrevWrapIndex >= softWraps.size()
                                 ? null
                                 : softWraps.get(currentOrPrevWrapIndex);
    if (currentOrPrevWrap != null && currentOrPrevWrap.getStart() > visualLineStartOffset) {
      visualLineStartOffset = currentOrPrevWrap.getStart();
    }
    int nextFoldingIndex = myFoldingModel.getLastCollapsedRegionBefore(visualLineStartOffset) + 1;
    init(
      view,
      align ? view.offsetToVisualPosition(offset, false, false).line : -1,
      visualLineStartOffset,
      myDocument.lineNumber(visualLineStartOffset),
      currentOrPrevWrapIndex,
      nextFoldingIndex,
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
    myView = view;
    myDocument = document;
    myFoldingModel = view.getFoldingModel();
    mySoftWrapModel = view.getSoftWrapModel();
    myInlayModel = view.getInlayModel();
    init(
      view,
      visualLinesIterator.getVisualLine(),
      visualLinesIterator.getVisualLineStartOffset(),
      visualLinesIterator.getStartLogicalLine(),
      visualLinesIterator.getStartOrPrevWrapIndex(),
      visualLinesIterator.getStartFoldingIndex(),
      quickEvaluationListener,
      align
    );
  }

  @Override
  public boolean hasNext() {
    return !(myFoldRegion instanceof CustomFoldRegion) &&
           (mySegmentStartOffset == getCurrentFoldRegionStartOffset() || myFragmentIterator == null || myFragmentIterator.hasNext());
  }

  @Override
  public Fragment next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    myPrevX = myCurrentX;
    if (mySegmentStartOffset == getCurrentFoldRegionStartOffset()) {
      myDelegate = null;
      myFoldRegion = myRegions[myCurrentFoldRegionIndex];
      assert myFoldRegion.isValid();
      myFoldRegionColumns = getFoldRegionWidthInColumns();
      mySegmentStartOffset = myFoldRegion.getEndOffset();
      myCurrentX += getFoldRegionWidthInPixels();
      myCurrentVisualColumn += myFoldRegionColumns;
      myCurrentStartLogicalLine = myCurrentEndLogicalLine;
      myCurrentEndLogicalLine = myDocument.lineNumber(mySegmentStartOffset);
      myCurrentFoldRegionIndex++;
      myFragmentIterator = null;
      myCurrentInlayIndex = 0;
      setInlaysAndFragmentIterator(null);
    } else if (myFragmentIterator == null) {
      myDelegate = null;
      myFoldRegion = null;
      myCurrentStartLogicalLine = myCurrentEndLogicalLine;
      Inlay<?> inlay = myInlays.get(myCurrentInlayIndex);
      //noinspection lossy-conversions
      myCurrentX += alignToInt(inlay.getWidthInPixels());
      myCurrentVisualColumn++;
      myCurrentInlayIndex++;
      if (myCurrentInlayIndex >= myInlays.size() || myInlays.get(myCurrentInlayIndex).getOffset() > inlay.getOffset()) {
        setFragmentIterator();
      }
    } else {
      myDelegate = myFragmentIterator.next();
      myFoldRegion = null;
      myCurrentX = myDelegate.getEndX();
      myCurrentVisualColumn = myDelegate.getEndVisualColumn();
      myCurrentStartLogicalLine = myCurrentEndLogicalLine;
      if (!myFragmentIterator.hasNext()) {
        if (myCurrentInlayIndex < myInlays.size()) {
          myFragmentIterator = null;
        } else {
          mySegmentStartOffset = mySegmentEndOffset;
        }
      }
    }
    myCurrentStartLogicalLineStart = myDocument.lineStartOffset(myCurrentStartLogicalLine);
    return myFragment;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  private void init(
    @NotNull EditorView view,
    int visualLine,
    int startOffset,
    int startLogicalLine,
    int currentOrPrevWrapIndex,
    int nextFoldingIndex,
    @Nullable Runnable quickEvaluationListener,
    boolean align
  ) {
    myQuickEvaluationListener = quickEvaluationListener;
    EditorImpl editor = view.getEditor();
    myScaleContext = ScaleContext.create(editor.getContentComponent());
    if (align && visualLine != -1 && editor.isRightAligned()) {
      myFragment = new RightAlignedFragment(view.getRightAlignmentLineStartX(visualLine) - getInsets().left);
    }
    FoldingModelEx foldingModel = view.getFoldingModel();
    FoldRegion[] regions = foldingModel.fetchTopLevel();
    myRegions = regions == null ? FoldRegion.EMPTY_ARRAY : regions;
    List<? extends SoftWrapEx> softWraps = mySoftWrapModel.getRegisteredSoftWrapsEx();
    SoftWrapEx currentOrPrevWrap = currentOrPrevWrapIndex < 0 || currentOrPrevWrapIndex >= softWraps.size()
                                   ? null
                                   : softWraps.get(currentOrPrevWrapIndex);
    SoftWrapEx followingWrap = currentOrPrevWrapIndex + 1 < 0 || currentOrPrevWrapIndex + 1 >= softWraps.size()
                               ? null
                               : softWraps.get(currentOrPrevWrapIndex + 1);
    myVisualLineStartOffset = mySegmentStartOffset = startOffset;
    myCurrentFoldRegionIndex = nextFoldingIndex;
    myCurrentEndLogicalLine = startLogicalLine;
    myCurrentX = getInsets().left;
    if (mySegmentStartOffset == 0) {
      myCurrentX += getPrefixTextWidthInPixels();
    } else if (currentOrPrevWrap != null && mySegmentStartOffset == currentOrPrevWrap.getStart()) {
      //noinspection lossy-conversions
      myCurrentX += alignToInt(currentOrPrevWrap.getIndentInPixels());
      myCurrentVisualColumn = currentOrPrevWrap.getIndentInColumns();
    }
    myNextWrap = followingWrap;
    setInlaysAndFragmentIterator(currentOrPrevWrap);
  }

  private double alignToInt(int width) {
    return PaintUtil.alignToInt(width, myScaleContext);
  }

  private int getNextWrapOffset() {
    return myNextWrap == null ? Integer.MAX_VALUE : myNextWrap.getStart();
  }

  private void setInlaysAndFragmentIterator(@Nullable SoftWrapEx startCustomWrap) {
    mySegmentEndOffset = getCurrentFoldRegionStartOffset();
    if (mySegmentEndOffset < mySegmentStartOffset) {
      myView.getEditor().throwEditorInvariantBroken(assertMessage());
    }
    if (mySegmentEndOffset > mySegmentStartOffset) {
      mySegmentEndOffset = Math.min(getNextWrapOffset(), Math.min(mySegmentEndOffset, myDocument.lineEndOffset(myCurrentEndLogicalLine)));
      boolean normalLineEnd = mySegmentEndOffset < getCurrentFoldRegionStartOffset() &&
                              (mySegmentEndOffset < getNextWrapOffset() || myNextWrap instanceof CustomWrapToSoftWrapAdapter);
      int endOffset = Math.max(mySegmentStartOffset, mySegmentEndOffset - (normalLineEnd ? 0 : 1));
      myInlays = myInlayModel.getInlineElementsInRange(mySegmentStartOffset, endOffset); // including inlays at line end
      if (myNextWrap instanceof CustomWrapToSoftWrapAdapter customWrap && customWrap.getStart() == mySegmentEndOffset) {
        for (int i = myInlays.size() - 1; i >= 0; i--) {
          Inlay<?> inlay = myInlays.get(i);
          if (inlay.getOffset() < mySegmentEndOffset) {
            break;
          }
          if (!inlay.isRelatedToPrecedingText()) {
            myInlays.remove(i);
          }
        }
      }
      if (startCustomWrap instanceof CustomWrapToSoftWrapAdapter customWrap &&
          customWrap.getStart() == mySegmentStartOffset) {
        for (final var it = myInlays.iterator(); it.hasNext();) {
          Inlay<?> inlay = it.next();
          if (inlay.getOffset() > mySegmentStartOffset) {
            break;
          }
          if (inlay.isRelatedToPrecedingText()) {
            it.remove();
          }
        }
      }
      if (myInlays.isEmpty() || myInlays.getFirst().getOffset() > mySegmentStartOffset) {
        setFragmentIterator();
      }
    }
  }

  private void setFragmentIterator() {
    int startOffset = myCurrentInlayIndex > 0 ? myInlays.get(myCurrentInlayIndex - 1).getOffset() : mySegmentStartOffset;
    int endOffset = myCurrentInlayIndex < myInlays.size() ? myInlays.get(myCurrentInlayIndex).getOffset() : mySegmentEndOffset;
    int lineStartOffset = myDocument.lineStartOffset(myCurrentEndLogicalLine);
    if (myCurrentEndLogicalLine < myDocument.lineCount()) { // handle empty document case
      LineLayout lineLayout = getTextLayoutCache().getLineLayout(myDocument, myCurrentEndLogicalLine);
      myFragmentIterator = lineLayout.getFragmentsInVisualOrder(
        myView,
        myDocument,
        myCurrentEndLogicalLine,
        startOffset - lineStartOffset,
        endOffset - lineStartOffset,
        myCurrentVisualColumn,
        myCurrentX,
        myQuickEvaluationListener
      );
    } else {
      myFragmentIterator = Collections.emptyIterator();
    }
  }

  private int getCurrentFoldRegionStartOffset() {
    if (myCurrentFoldRegionIndex >= myRegions.length) {
      return Integer.MAX_VALUE;
    }
    int nextFoldingOffset = myRegions[myCurrentFoldRegionIndex].getStartOffset();
    if (nextFoldingOffset < getNextWrapOffset()) {
      return nextFoldingOffset;
    }
    return Integer.MAX_VALUE;
  }

  private float getFoldRegionWidthInPixels() {
    if (myFoldRegion instanceof CustomFoldRegion region) {
      return region.getWidthInPixels();
    }
    return getFoldRegionLayout().getWidth();
  }

  private int getFoldRegionWidthInColumns() {
    int maxVisualColumn = 0;
    for (var fragment : getFoldRegionLayout().getFragmentsInVisualOrder(0)) {
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
    return getFoldRegionWidthInPixels();
  }

  // offset is absolute
  private float getXForOffsetInsideFoldRegion(int offset) {
    return offset < myFoldRegion.getEndOffset() ? 0 : getFoldRegionWidthInPixels();
  }

  private @NotNull Insets getInsets() {
    return myView.getInsets();
  }

  private @NotNull LineLayout getFoldRegionLayout() {
    return myView.getFoldRegionLayout(myFoldRegion);
  }

  private float getPrefixTextWidthInPixels() {
    return myView.getPrefixTextWidthInPixels();
  }

  private @NotNull LogicalPosition offsetToLogicalPosition(int offset) {
    return myView.offsetToLogicalPosition(offset);
  }

  private @NotNull TextLayoutCache getTextLayoutCache() {
    return myView.getTextLayoutCache();
  }

  class Fragment {
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
      return myCurrentVisualColumn - (myFoldRegion != null ? myFoldRegionColumns : 1);
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
      return  myDelegate != null ? myDelegate.getStartX() : myPrevX;
    }

    float getEndX() {
      return myCurrentX;
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
        return myDelegate.xToVisualColumn(x);
      } else if (myFoldRegion != null) {
        VisualColumn column = getVisualColumnForXInsideFoldRegion(x - getStartX());
        column.column += getStartVisualColumn();
        return column;
      } else {
        boolean closerToStart = x < (getStartX() + getEndX()) / 2;
        return new VisualColumn(myCurrentVisualColumn - (closerToStart ? 1 : 0), !closerToStart);
      }
    }

    float visualColumnToX(int column) {
      if (myDelegate != null) {
        return myDelegate.visualColumnToX(column);
      }
      if (myFoldRegion != null) {
        return getStartX() + getXForVisualColumnInsideFoldRegion(column - myCurrentVisualColumn + myFoldRegionColumns);
      }
      return column == myCurrentVisualColumn ? getEndX() : getStartX();
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
        return myDelegate.offsetToX(offset - myCurrentStartLogicalLineStart);
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
      return myDelegate.offsetToX(startX, startOffset - lineStartOffset, offset - lineStartOffset);
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

  final class RightAlignedFragment extends Fragment {
    private final float xOffset;

    RightAlignedFragment(float offset) {
      xOffset = offset;
    }

    @Override
    float offsetToX(int offset) {
      return super.offsetToX(offset) + xOffset;
    }

    @Override
    float offsetToX(float startX, int startOffset, int offset) {
      return super.offsetToX(startX - xOffset, startOffset, offset) + xOffset;
    }

    @Override
    float getEndX() {
      return super.getEndX() + xOffset;
    }

    @Override
    float getStartX() {
      return super.getStartX() + xOffset;
    }

    @Override
    float visualColumnToX(int column) {
      return super.visualColumnToX(column) + xOffset;
    }

    @Override
    VisualColumn xToVisualColumn(float x) {
      return super.xToVisualColumn(x - xOffset);
    }
  }

  private @NotNull String assertMessage() {
    String startOffset = "startOffset: " + mySegmentStartOffset;
    String endOffset   = "endOffset: " + mySegmentEndOffset;
    String foldIndex   = "foldIndex: " + myCurrentFoldRegionIndex;
    String foldCount   = "foldCount: " + (myRegions != null ? myRegions.length : "null");
    String foldRegion  = "fold" + ((myRegions != null && myCurrentFoldRegionIndex < myRegions.length) ? myRegions[myCurrentFoldRegionIndex].toString() : "");
    String inlayIndex  = "inlayIndex: " + myCurrentInlayIndex;
    String inlayCount  = "inlayCount: " + (myInlays != null ? myInlays.size() : "null");
    String inlay       = "inlay: " + ((myInlays != null && myCurrentInlayIndex < myInlays.size()) ? myInlays.get(myCurrentInlayIndex).toString() : "");
    String textLength  = "textLength: " + myDocument.length();
    return String.join(
      ", ",
      startOffset,
      endOffset,
      foldIndex,
      foldCount,
      foldRegion,
      inlayIndex,
      inlayCount,
      inlay,
      textLength
    );
  }
}
