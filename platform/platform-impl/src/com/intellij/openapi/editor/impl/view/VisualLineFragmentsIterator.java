// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterator over visual line's fragments. Fragment's text has the same font and directionality. Collapsed fold regions are also represented
 * as fragments.
 */
class VisualLineFragmentsIterator implements Iterator<VisualLineFragmentsIterator.Fragment> {

  @NotNull
  static Iterable<Fragment> create(@NotNull EditorView view, int offset, boolean beforeSoftWrap) {
    return create(view, offset, beforeSoftWrap, false);
  }

  @NotNull
  static Iterable<Fragment> create(@NotNull EditorView view, int offset, boolean beforeSoftWrap, boolean align) {
    return () -> new VisualLineFragmentsIterator(view, offset, beforeSoftWrap, align);
  }

  /**
   * If {@code quickEvaluationListener} is provided, quick approximate iteration mode becomes enabled, listener will be invoked
   * if approximation will in fact be used during width calculation.
   */
  @NotNull
  static Iterable<Fragment> create(@NotNull EditorView view,
                                   @NotNull VisualLinesIterator visualLinesIterator,
                                   @Nullable Runnable quickEvaluationListener,
                                   boolean align) {
    return () -> new VisualLineFragmentsIterator(view, visualLinesIterator, quickEvaluationListener, align);
  }

  private EditorView myView;
  private Document myDocument;
  private FoldRegion[] myRegions;
  private Fragment myFragment = new Fragment();
  private int myVisualLineStartOffset;
  private Runnable myQuickEvaluationListener;
  
  private int mySegmentStartOffset;
  private int mySegmentEndOffset;
  private int myCurrentFoldRegionIndex;
  private Iterator<LineLayout.VisualFragment> myFragmentIterator;
  private List<Inlay> myInlays;
  private int myCurrentInlayIndex;
  private float myCurrentX;
  private int myCurrentVisualColumn;
  private LineLayout.VisualFragment myDelegate;
  private FoldRegion myFoldRegion;
  private int myCurrentStartLogicalLine;
  private int myCurrentEndLogicalLine;
  private int myNextWrapOffset;

  private VisualLineFragmentsIterator(EditorView view, int offset, boolean beforeSoftWrap, boolean align) {
    EditorImpl editor = view.getEditor();
    int visualLineStartOffset = EditorUtil.getNotFoldedLineStartOffset(editor, offset);
    
    SoftWrapModelImpl softWrapModel = editor.getSoftWrapModel();
    List<? extends SoftWrap> softWraps = softWrapModel.getRegisteredSoftWraps();
    int currentOrPrevWrapIndex = softWrapModel.getSoftWrapIndex(offset);
    if (currentOrPrevWrapIndex < 0) {
      currentOrPrevWrapIndex = - currentOrPrevWrapIndex - 2;
    }
    else if (beforeSoftWrap) {
      currentOrPrevWrapIndex--;
    }
    SoftWrap currentOrPrevWrap = currentOrPrevWrapIndex < 0 || currentOrPrevWrapIndex >= softWraps.size() ? null :
                                 softWraps.get(currentOrPrevWrapIndex);
    if (currentOrPrevWrap != null && currentOrPrevWrap.getStart() > visualLineStartOffset) {
      visualLineStartOffset = currentOrPrevWrap.getStart();
    }
    
    int nextFoldingIndex = editor.getFoldingModel().getLastCollapsedRegionBefore(visualLineStartOffset) + 1;

    init(view,
         align ? editor.offsetToVisualPosition(offset).line : -1,
         visualLineStartOffset,
         editor.getDocument().getLineNumber(visualLineStartOffset),
         currentOrPrevWrapIndex,
         nextFoldingIndex,
         null,
         align);
  }

  private VisualLineFragmentsIterator(@NotNull EditorView view, @NotNull VisualLinesIterator visualLinesIterator,
                                      @Nullable Runnable quickEvaluationListener, boolean align) {
    assert !visualLinesIterator.atEnd();
    init(view,
         visualLinesIterator.getVisualLine(),
         visualLinesIterator.getVisualLineStartOffset(),
         visualLinesIterator.getStartLogicalLine(),
         visualLinesIterator.getStartOrPrevWrapIndex(),
         visualLinesIterator.getStartFoldingIndex(),
         quickEvaluationListener,
         align);
  }

  private void init(EditorView view, int visualLine, int startOffset, int startLogicalLine, int currentOrPrevWrapIndex, int nextFoldingIndex,
                    @Nullable Runnable quickEvaluationListener, boolean align) {
    myQuickEvaluationListener = quickEvaluationListener;
    myView = view;
    EditorImpl editor = view.getEditor();
    if (align && visualLine != -1 && editor.isRightAligned()) {
      myFragment = new RightAlignedFragment(view.getRightAlignmentLineStartX(visualLine) - myView.getInsets().left);
    }
    myDocument = editor.getDocument();
    FoldingModelEx foldingModel = editor.getFoldingModel();
    FoldRegion[] regions = foldingModel.fetchTopLevel();
    myRegions = regions == null ? FoldRegion.EMPTY_ARRAY : regions;
    SoftWrapModelImpl softWrapModel = editor.getSoftWrapModel();
    List<? extends SoftWrap> softWraps = softWrapModel.getRegisteredSoftWraps();
    SoftWrap currentOrPrevWrap = currentOrPrevWrapIndex < 0 || currentOrPrevWrapIndex >= softWraps.size() ? null :
                                 softWraps.get(currentOrPrevWrapIndex);
    SoftWrap followingWrap = currentOrPrevWrapIndex + 1 < 0 || currentOrPrevWrapIndex + 1 >= softWraps.size() ? null :
                             softWraps.get(currentOrPrevWrapIndex + 1);

    myVisualLineStartOffset = mySegmentStartOffset = startOffset;

    myCurrentFoldRegionIndex = nextFoldingIndex;
    myCurrentEndLogicalLine = startLogicalLine;
    myCurrentX = myView.getInsets().left;
    if (mySegmentStartOffset == 0) {
      myCurrentX += myView.getPrefixTextWidthInPixels();
    }
    else if (currentOrPrevWrap != null && mySegmentStartOffset == currentOrPrevWrap.getStart()) {
      myCurrentX += currentOrPrevWrap.getIndentInPixels();
      myCurrentVisualColumn = currentOrPrevWrap.getIndentInColumns();
    }
    myNextWrapOffset = followingWrap == null ? Integer.MAX_VALUE : followingWrap.getStart();
    setInlaysAndFragmentIterator();
  }

  private void setInlaysAndFragmentIterator() {
    mySegmentEndOffset = getCurrentFoldRegionStartOffset();
    assert mySegmentEndOffset >= mySegmentStartOffset;
    if (mySegmentEndOffset > mySegmentStartOffset) {
      mySegmentEndOffset = Math.min(myNextWrapOffset, Math.min(mySegmentEndOffset, myDocument.getLineEndOffset(myCurrentEndLogicalLine)));
      boolean normalLineEnd = mySegmentEndOffset < getCurrentFoldRegionStartOffset() && mySegmentEndOffset < myNextWrapOffset;
      myInlays = myView.getEditor().getInlayModel().getInlineElementsInRange(
        mySegmentStartOffset,
        mySegmentEndOffset - (normalLineEnd ? 0 : 1)); // including inlays at line end
      if (myInlays.isEmpty() || myInlays.get(0).getOffset() > mySegmentStartOffset) {
        setFragmentIterator();
      }
    }
  }

  private void setFragmentIterator() {
    int startOffset = myCurrentInlayIndex > 0 ? myInlays.get(myCurrentInlayIndex - 1).getOffset() : mySegmentStartOffset;
    int endOffset = myCurrentInlayIndex < myInlays.size() ? myInlays.get(myCurrentInlayIndex).getOffset() : mySegmentEndOffset;
    int lineStartOffset = myDocument.getLineStartOffset(myCurrentEndLogicalLine);
    myFragmentIterator = myView.getTextLayoutCache().getLineLayout(myCurrentEndLogicalLine).
      getFragmentsInVisualOrder(myView, myCurrentEndLogicalLine, myCurrentX, myCurrentVisualColumn,
                                startOffset - lineStartOffset, endOffset - lineStartOffset,
                                myQuickEvaluationListener);
  }

  private int getCurrentFoldRegionStartOffset() {
    if (myCurrentFoldRegionIndex >= myRegions.length) {
      return Integer.MAX_VALUE;
    }
    int nextFoldingOffset = myRegions[myCurrentFoldRegionIndex].getStartOffset();
    return nextFoldingOffset < myNextWrapOffset ? nextFoldingOffset : Integer.MAX_VALUE;
  }
  
  private float getFoldRegionWidthInPixels(FoldRegion foldRegion) {
    return myView.getFoldRegionLayout(foldRegion).getWidth();
  }

  private int getFoldRegionWidthInColumns(FoldRegion foldRegion) {
    int maxVisualColumn = 0;
    for (LineLayout.VisualFragment fragment : myView.getFoldRegionLayout(foldRegion).getFragmentsInVisualOrder(0)) {
      maxVisualColumn = fragment.getEndVisualColumn();
    }
    return maxVisualColumn;
  }

  private int[] getVisualColumnForXInsideFoldRegion(FoldRegion foldRegion, float x) {
    LineLayout layout = myView.getFoldRegionLayout(foldRegion);
    for (LineLayout.VisualFragment fragment : layout.getFragmentsInVisualOrder(0)) {
      if (x <= fragment.getEndX()) {
        return fragment.xToVisualColumn(x);
      }
    }
    return new int[] {getFoldRegionWidthInColumns(foldRegion), 1};
  }

  private float getXForVisualColumnInsideFoldRegion(FoldRegion foldRegion, int column) {
    LineLayout layout = myView.getFoldRegionLayout(foldRegion);
    for (LineLayout.VisualFragment fragment : layout.getFragmentsInVisualOrder(0)) {
      if (column <= fragment.getEndVisualColumn()) {
        return fragment.visualColumnToX(column);
      }
    }
    return getFoldRegionWidthInPixels(foldRegion);
  }
  
  // offset is absolute
  private float getXForOffsetInsideFoldRegion(FoldRegion foldRegion, int offset) {
    return offset < foldRegion.getEndOffset() ? 0 : getFoldRegionWidthInPixels(foldRegion);
  }

  @Override
  public boolean hasNext() {
    return mySegmentStartOffset == getCurrentFoldRegionStartOffset() || myFragmentIterator == null || myFragmentIterator.hasNext();
  }

  @Override
  public Fragment next() {
    if (!hasNext()) throw new NoSuchElementException();
    if (mySegmentStartOffset == getCurrentFoldRegionStartOffset()) {
      myDelegate = null;
      myFoldRegion = myRegions[myCurrentFoldRegionIndex];
      assert myFoldRegion.isValid();

      mySegmentStartOffset = myFoldRegion.getEndOffset();
      myCurrentX += getFoldRegionWidthInPixels(myFoldRegion);
      myCurrentVisualColumn += getFoldRegionWidthInColumns(myFoldRegion);
      myCurrentStartLogicalLine = myCurrentEndLogicalLine;
      myCurrentEndLogicalLine = myDocument.getLineNumber(mySegmentStartOffset);
      myCurrentFoldRegionIndex++;
      myFragmentIterator = null;
      myCurrentInlayIndex = 0;
      setInlaysAndFragmentIterator();
    }
    else if (myFragmentIterator == null) {
      myDelegate = null;
      myFoldRegion = null;
      myCurrentStartLogicalLine = myCurrentEndLogicalLine;
      Inlay inlay = myInlays.get(myCurrentInlayIndex);
      myCurrentX += inlay.getWidthInPixels();
      myCurrentVisualColumn++;
      myCurrentInlayIndex++;
      if (myCurrentInlayIndex >= myInlays.size() || myInlays.get(myCurrentInlayIndex).getOffset() > inlay.getOffset()) {
        setFragmentIterator();
      }
    }
    else {
      myDelegate = myFragmentIterator.next();
      myFoldRegion = null;
      myCurrentX = myDelegate.getEndX();
      myCurrentVisualColumn = myDelegate.getEndVisualColumn();
      myCurrentStartLogicalLine = myCurrentEndLogicalLine;
      if (!myFragmentIterator.hasNext()) {
        if (myCurrentInlayIndex < myInlays.size()) {
          myFragmentIterator = null;
        }
        else {
          mySegmentStartOffset = mySegmentEndOffset;
        }
      }
    }
    return myFragment;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  class Fragment {
    int getVisualLineStartOffset() {
      return myVisualLineStartOffset;
    }

    boolean isCollapsedFoldRegion() {
      return myFoldRegion != null;
    }

    int getMinLogicalColumn() {
      return myDelegate == null ? myView.offsetToLogicalPosition(getMinOffset()).column : myDelegate.getMinLogicalColumn();
    }

    int getMaxLogicalColumn() {
      return myDelegate == null ? myView.offsetToLogicalPosition(getMaxOffset()).column : myDelegate.getMaxLogicalColumn();
    }

    int getStartLogicalColumn() {
      return myDelegate == null ? myView.offsetToLogicalPosition(getStartOffset()).column : myDelegate.getStartLogicalColumn();
    }

    int getEndLogicalColumn() {
      return myDelegate == null ? myView.offsetToLogicalPosition(getEndOffset()).column : myDelegate.getEndLogicalColumn();
    }

    int getStartVisualColumn() {
      return myDelegate != null
             ? myDelegate.getStartVisualColumn()
             : myCurrentVisualColumn - (myFoldRegion != null ? getFoldRegionWidthInColumns(myFoldRegion) : 1);
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
      return  myDelegate != null ? myDelegate.getStartX()
                                 : myCurrentX - (myFoldRegion != null ? getFoldRegionWidthInPixels(myFoldRegion) 
                                                                      : getCurrentInlay().getWidthInPixels());
    }

    float getEndX() {
      return myCurrentX;
    }

    // column is expected to be between minLogicalColumn and maxLogicalColumn for this fragment
    int logicalToVisualColumn(int column) {
      return myDelegate != null ? myDelegate.logicalToVisualColumn(column)
             : myFoldRegion != null ? myCurrentVisualColumn - getFoldRegionWidthInColumns(myFoldRegion) : getEndVisualColumn();
    }

    // column is expected to be between startVisualColumn and endVisualColumn for this fragment
    int visualToLogicalColumn(int column) {
      return myDelegate != null ? myDelegate.visualToLogicalColumn(column)
                                : myFoldRegion != null ? column == myCurrentVisualColumn ? getEndLogicalColumn() : getStartLogicalColumn()
                                                       : getEndLogicalColumn();
    }

    // returns array of two elements 
    // - first one is visual column, 
    // - second one is 1 if target location is closer to larger columns and 0 otherwise
    int[] xToVisualColumn(float x) {
      if (myDelegate != null) {
        return myDelegate.xToVisualColumn(x);
      }
      else if (myFoldRegion != null) {
        int[] column = getVisualColumnForXInsideFoldRegion(myFoldRegion, x - getStartX());
        column[0] += getStartVisualColumn();
        return column;
      }
      else {
        boolean closerToStart = x < (getStartX() + getEndX()) / 2;
        return new int[]{myCurrentVisualColumn - (closerToStart ? 1 : 0), closerToStart ? 0 : 1};
      }
    }

    float visualColumnToX(int column) {
      return myDelegate != null
             ? myDelegate.visualColumnToX(column)
             : myFoldRegion != null
               ? getStartX() + getXForVisualColumnInsideFoldRegion(myFoldRegion, column - myCurrentVisualColumn +
                                                                                 getFoldRegionWidthInColumns(myFoldRegion))
               : column == myCurrentVisualColumn ? getEndX() : getStartX();
    }

    int getVisualLength() {
      if (myDelegate != null) return myDelegate.getLength();
      if (myFoldRegion != null) {
        int length = 0;
        for (LineLayout.VisualFragment fragment : myView.getFoldRegionLayout(myFoldRegion).getFragmentsInVisualOrder(0)) {
          length += fragment.getLength();
        }
        return length;
      }
      return 0;
    }

    // returned offset is visual and relative (counted from fragment's start)
    int visualColumnToOffset(int relativeVisualColumn) {
      if (myDelegate != null) return myDelegate.visualColumnToOffset(relativeVisualColumn);
      if (myFoldRegion != null) {
        int relativeOffset = 0;
        for (LineLayout.VisualFragment fragment : myView.getFoldRegionLayout(myFoldRegion).getFragmentsInVisualOrder(0)) {
          if (relativeVisualColumn >= fragment.getStartVisualColumn() && relativeVisualColumn <= fragment.getEndVisualColumn()) {
            return relativeOffset + fragment.visualColumnToOffset(relativeVisualColumn - fragment.getStartVisualColumn());
          }
          relativeOffset += fragment.getLength();
        }
      }
      return 0;
    }

    // absolute
    int getStartOffset() {
      return myDelegate != null ? myDelegate.getStartOffset() + myDocument.getLineStartOffset(myCurrentStartLogicalLine)
                                : myFoldRegion != null ? myFoldRegion.getStartOffset() : getCurrentInlay().getOffset();
    }

    // absolute
    int getEndOffset() {
      return myDelegate != null ? myDelegate.getEndOffset() + myDocument.getLineStartOffset(myCurrentStartLogicalLine)
                                : myFoldRegion != null ? myFoldRegion.getEndOffset() : getCurrentInlay().getOffset();
    }

    // absolute
    int getMinOffset() {
      return myDelegate != null ? myDelegate.getMinOffset() + myDocument.getLineStartOffset(myCurrentStartLogicalLine)
                                : myFoldRegion != null ? myFoldRegion.getStartOffset() : getCurrentInlay().getOffset();
    }

    // absolute
    int getMaxOffset() {
      return myDelegate != null ? myDelegate.getMaxOffset() + myDocument.getLineStartOffset(myCurrentStartLogicalLine)
                                : myFoldRegion != null ? myFoldRegion.getEndOffset() : getCurrentInlay().getOffset();
    }

    // offset is absolute
    float offsetToX(int offset) {
      return myDelegate != null
             ? myDelegate.offsetToX(offset - myDocument.getLineStartOffset(myCurrentStartLogicalLine))
             : myFoldRegion != null ? getStartX() + getXForOffsetInsideFoldRegion(myFoldRegion, offset) : getEndX();
    }

    // offsets are absolute
    float offsetToX(float startX, int startOffset, int offset) {
      assert myDelegate != null;
      int lineStartOffset = myDocument.getLineStartOffset(myCurrentStartLogicalLine);
      return myDelegate.offsetToX(startX, startOffset - lineStartOffset, offset - lineStartOffset);
    }
    
    boolean isRtl() {
      return myDelegate != null && myDelegate.isRtl();
    }
    
    FoldRegion getCurrentFoldRegion() {
      return myFoldRegion;
    }

    Inlay getCurrentInlay() {
      if (myDelegate != null || myFoldRegion != null) return null;
      return myInlays.get(myCurrentInlayIndex - 1);
    }

    // offsets are visual (relative to fragment's start)
    void draw(Graphics2D g, float x, float y, int startRelativeOffset, int endRelativeOffset) {
      if (myDelegate != null) {
        myDelegate.draw(g, x, y, startRelativeOffset, endRelativeOffset);
      }
      else if (myFoldRegion != null) {
        int relativeOffset = 0;
        for (LineLayout.VisualFragment fragment : myView.getFoldRegionLayout(myFoldRegion).getFragmentsInVisualOrder(x)) {
          int relativeOffsetEnd = relativeOffset + fragment.getLength();
          if (relativeOffset < endRelativeOffset && relativeOffsetEnd > startRelativeOffset) {
            fragment.draw(g, fragment.getStartX(), y,
                          Math.max(0, startRelativeOffset - relativeOffset), 
                          Math.min(relativeOffsetEnd, endRelativeOffset) - relativeOffset);
          }
          relativeOffset = relativeOffsetEnd;
        }
      }
    }
  }

  class RightAlignedFragment extends Fragment {
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
    int[] xToVisualColumn(float x) {
      return super.xToVisualColumn(x - xOffset);
    }
  }
}
