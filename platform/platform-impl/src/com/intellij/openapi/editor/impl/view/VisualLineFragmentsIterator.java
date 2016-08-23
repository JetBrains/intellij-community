/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

  static Iterable<Fragment> create(final EditorView view, final int offset, final boolean beforeSoftWrap) {
    return new Iterable<Fragment>() {
      @Override
      public Iterator<Fragment> iterator() {
        return new VisualLineFragmentsIterator(view, offset, beforeSoftWrap, null);
      }
    };
  }
  
  /**
   * If <code>quickEvaluationListener</code> is provided, quick approximate iteration mode becomes enabled, listener will be invoked
   * if approximation will in fact be used during width calculation.
   */
  static Iterable<Fragment> create(final EditorView view, @NotNull final VisualLinesIterator visualLinesIterator, 
                                   @Nullable final Runnable quickEvaluationListener) {
    return new Iterable<Fragment>() {
      @Override
      public Iterator<Fragment> iterator() {
        return new VisualLineFragmentsIterator(view, visualLinesIterator, quickEvaluationListener);
      }
    };
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

  private VisualLineFragmentsIterator(EditorView view, int offset, boolean beforeSoftWrap, @Nullable Runnable quickEvaluationListener) {
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
         visualLineStartOffset, 
         editor.getDocument().getLineNumber(visualLineStartOffset), 
         currentOrPrevWrapIndex, 
         nextFoldingIndex, 
         quickEvaluationListener);
  }

  public VisualLineFragmentsIterator(@NotNull EditorView view, @NotNull VisualLinesIterator visualLinesIterator, 
                                     @Nullable Runnable quickEvaluationListener) {
    assert !visualLinesIterator.atEnd();
    init(view, 
         visualLinesIterator.getVisualLineStartOffset(), 
         visualLinesIterator.getStartLogicalLine(), 
         visualLinesIterator.getStartOrPrevWrapIndex(), 
         visualLinesIterator.getStartFoldingIndex(),
         quickEvaluationListener);
  }

  private void init(EditorView view, int startOffset, int startLogicalLine, int currentOrPrevWrapIndex, int nextFoldingIndex,
                    @Nullable Runnable quickEvaluationListener) {
    myQuickEvaluationListener = quickEvaluationListener;
    myView = view;
    EditorImpl editor = view.getEditor();
    myDocument = editor.getDocument();
    FoldingModelEx foldingModel = editor.getFoldingModel();
    FoldRegion[] regions = foldingModel.fetchTopLevel();
    myRegions = regions == null ? FoldRegion.EMPTY_ARRAY : regions;
    SoftWrapModelImpl softWrapModel = editor.getSoftWrapModel();
    List<? extends SoftWrap> softWraps = softWrapModel.getRegisteredSoftWraps();
    SoftWrap currentOrPrevWrap = currentOrPrevWrapIndex < 0 || currentOrPrevWrapIndex >= softWraps.size() ? null :
                                 softWraps.get(currentOrPrevWrapIndex);
    SoftWrap followingWrap = (currentOrPrevWrapIndex + 1) < 0 || (currentOrPrevWrapIndex + 1) >= softWraps.size() ? null :
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
    if (mySegmentEndOffset > mySegmentStartOffset) {
      mySegmentEndOffset = Math.min(myNextWrapOffset, Math.min(mySegmentEndOffset, myDocument.getLineEndOffset(myCurrentEndLogicalLine)));
      boolean normalLineEnd = mySegmentEndOffset < getCurrentFoldRegionStartOffset() && mySegmentEndOffset < myNextWrapOffset;
      myInlays = myView.getEditor().getInlayModel().getInlineElementsInRange(
        mySegmentStartOffset,
        mySegmentEndOffset - ((normalLineEnd && mySegmentEndOffset > mySegmentStartOffset) ? 0 : 1)); // including inlays at line end
      if (!myInlays.isEmpty() && myInlays.get(0).getOffset() == mySegmentStartOffset) {
        myCurrentInlayIndex = 0;
        myFragmentIterator = null;
      }
      else {
        myCurrentInlayIndex = -1;
        setFragmentIterator();
      }
    }
  }

  private void setFragmentIterator() {
    int startOffset = myCurrentInlayIndex < 0 ? mySegmentStartOffset : myInlays.get(myCurrentInlayIndex).getOffset();
    int nextIndex = getNextInlayIndex();
    int endOffset = nextIndex < myInlays.size() ? myInlays.get(nextIndex).getOffset() : mySegmentEndOffset;
    int lineStartOffset = myDocument.getLineStartOffset(myCurrentEndLogicalLine);
    myFragmentIterator = myView.getTextLayoutCache().getLineLayout(myCurrentEndLogicalLine).
      getFragmentsInVisualOrder(myView, myCurrentEndLogicalLine, myCurrentX, myCurrentVisualColumn,
                                startOffset - lineStartOffset, endOffset - lineStartOffset,
                                myQuickEvaluationListener);
  }

  private int getNextInlayIndex() {
    if (myCurrentInlayIndex < 0) return 0;
    if (myCurrentInlayIndex >= myInlays.size()) return myCurrentInlayIndex;
    int currentOffset = myInlays.get(myCurrentInlayIndex).getOffset();
    int nextIndex = myCurrentInlayIndex + 1;
    while (nextIndex < myInlays.size() && myInlays.get(nextIndex).getOffset() == currentOffset) nextIndex++;
    return nextIndex;
  }

  private List<Inlay> getCurrentInlays() {
    return myInlays.subList(myCurrentInlayIndex, getNextInlayIndex());
  }

  private int getCurrentInlaysWidth() {
    int width = 0;
    for (Inlay inlay : getCurrentInlays()) {
      width += inlay.getWidthInPixels();
    }
    return width;
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

  private static int getFoldRegionWidthInColumns(FoldRegion foldRegion) {
    return foldRegion.getPlaceholderText().length();
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
      setInlaysAndFragmentIterator();
    }
    else if (myFragmentIterator == null) {
      myDelegate = null;
      myFoldRegion = null;
      myCurrentStartLogicalLine = myCurrentEndLogicalLine;
      myCurrentX += getCurrentInlaysWidth();
      myCurrentVisualColumn++;
      setFragmentIterator();
    }
    else {
      myDelegate = myFragmentIterator.next();
      myFoldRegion = null;
      myCurrentX = myDelegate.getEndX();
      myCurrentVisualColumn = myDelegate.getEndVisualColumn();
      myCurrentStartLogicalLine = myCurrentEndLogicalLine;
      if (!myFragmentIterator.hasNext()) {
        myCurrentInlayIndex = getNextInlayIndex();
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
                                 : myCurrentX - (myFoldRegion != null ? getFoldRegionWidthInPixels(myFoldRegion) : getCurrentInlaysWidth());
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
                                : myFoldRegion != null ? (column == myCurrentVisualColumn ? getEndLogicalColumn() : getStartLogicalColumn())
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

    // absolute
    int getStartOffset() {
      return myDelegate != null ? myDelegate.getStartOffset() + myDocument.getLineStartOffset(myCurrentStartLogicalLine)
                                : myFoldRegion != null ? myFoldRegion.getStartOffset() : myInlays.get(myCurrentInlayIndex).getOffset();
    }

    // absolute
    int getEndOffset() {
      return myDelegate != null ? myDelegate.getEndOffset() + myDocument.getLineStartOffset(myCurrentStartLogicalLine)
                                : myFoldRegion != null ? myFoldRegion.getEndOffset() : myInlays.get(myCurrentInlayIndex).getOffset();
    }

    // absolute
    int getMinOffset() {
      return myDelegate != null ? myDelegate.getMinOffset() + myDocument.getLineStartOffset(myCurrentStartLogicalLine)
                                : myFoldRegion != null ? myFoldRegion.getStartOffset() : myInlays.get(myCurrentInlayIndex).getOffset();
    }

    // absolute
    int getMaxOffset() {
      return myDelegate != null ? myDelegate.getMaxOffset() + myDocument.getLineStartOffset(myCurrentStartLogicalLine)
                                : myFoldRegion != null ? myFoldRegion.getEndOffset() : myInlays.get(myCurrentInlayIndex).getOffset();
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

    List<Inlay> getCurrentInlays() {
      if (myDelegate != null || myFoldRegion != null) return null;
      return VisualLineFragmentsIterator.this.getCurrentInlays();
    }

    // columns are visual (relative to fragment's start)
    void draw(Graphics2D g, float x, float y, int startRelativeColumn, int endRelativeColumn) {
      if (myDelegate != null) {
        myDelegate.draw(g, x, y, startRelativeColumn, endRelativeColumn);
      }
      else if (myFoldRegion != null) {
        for (LineLayout.VisualFragment fragment : myView.getFoldRegionLayout(myFoldRegion).getFragmentsInVisualOrder(x)) {
          int fragmentStart = fragment.getStartVisualColumn();
          int fragmentEnd = fragment.getEndVisualColumn();
          if (fragmentStart < endRelativeColumn && fragmentEnd > startRelativeColumn) {
            fragment.draw(g, fragment.getStartX(), y,
                          Math.max(0, startRelativeColumn - fragmentStart), Math.min(fragmentEnd, endRelativeColumn) - fragmentStart);
          }
        }
      }
    }
  }
}
