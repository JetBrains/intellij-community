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
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;

import java.awt.*;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterator over visual line's fragments. Fragment's text has the same font and directionality. Collapsed fold regions are also represented
 * as fragments.
 */
class VisualLineFragmentsIterator implements Iterator<VisualLineFragmentsIterator.Fragment> {

  static Iterable<Fragment> create(final EditorView view, final int offset) {
    return new Iterable<Fragment>() {
      @Override
      public Iterator<Fragment> iterator() {
        return new VisualLineFragmentsIterator(view, offset);
      }
    };
  }
  
  private final EditorView myView;
  private final Document myDocument;
  private final FoldRegion[] myRegions;
  private final Fragment myFragment = new Fragment();
  
  private int mySegmentStartOffset;
  private int mySegmentEndOffset;
  private int myCurrentFoldRegionIndex;
  private Iterator<LineLayout.VisualFragment> myFragmentIterator;
  private float myCurrentX;
  private int myCurrentVisualColumn;
  private LineLayout.VisualFragment myDelegate;
  private FoldRegion myFoldRegion;
  private int myCurrentStartLogicalLine;
  private int myCurrentEndLogicalLine;

  private VisualLineFragmentsIterator(EditorView view, int offset) {
    myView = view;
    myDocument = view.getEditor().getDocument();
    FoldingModelEx foldingModel = view.getEditor().getFoldingModel();
    FoldRegion[] regions = foldingModel.fetchTopLevel();
    myRegions = regions == null ? FoldRegion.EMPTY_ARRAY : regions;
    mySegmentStartOffset = EditorUtil.getNotFoldedLineStartOffset(view.getEditor(), offset);
    myCurrentFoldRegionIndex = foldingModel.getLastCollapsedRegionBefore(mySegmentStartOffset) + 1;
    myCurrentEndLogicalLine = myDocument.getLineNumber(mySegmentStartOffset);
    myCurrentX = myCurrentEndLogicalLine == 0 ? myView.getPrefixTextWidthInPixels() : 0;
    setFragmentIterator();
  }

  private void setFragmentIterator() {
    mySegmentEndOffset = getCurrentFoldRegionStartOffset();
    if (mySegmentEndOffset > mySegmentStartOffset) {
      int line = myDocument.getLineNumber(mySegmentStartOffset);
      mySegmentEndOffset = Math.min(mySegmentEndOffset, myDocument.getLineEndOffset(line));
      int lineStartOffset = myDocument.getLineStartOffset(line);
      myFragmentIterator = myView.getLineLayout(line).getFragmentsInVisualOrder(myCurrentX, myCurrentVisualColumn,
                                                                                mySegmentStartOffset - lineStartOffset, 
                                                                                mySegmentEndOffset - lineStartOffset).iterator();
    }
  }

  private int getCurrentFoldRegionStartOffset() {
    return myCurrentFoldRegionIndex < myRegions.length ? myRegions[myCurrentFoldRegionIndex].getStartOffset() : Integer.MAX_VALUE;
  }
  
  private float getFoldRegionWidthInPixels(FoldRegion foldRegion) {
    return myView.getFoldRegionLayout(foldRegion).getWidth();
  }

  private static int getFoldRegionWidthInColumns(FoldRegion foldRegion) {
    return foldRegion.getPlaceholderText().length();
  }

  private int getVisualColumnForXInsideFoldRegion(FoldRegion foldRegion, float x) {
    LineLayout layout = myView.getFoldRegionLayout(foldRegion);
    for (LineLayout.VisualFragment fragment : layout.getFragmentsInVisualOrder(0)) {
      if (x <= fragment.getEndX()) {
        return fragment.xToVisualColumn(x);
      }
    }
    return getFoldRegionWidthInColumns(foldRegion);
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
    return mySegmentStartOffset == getCurrentFoldRegionStartOffset() || myFragmentIterator.hasNext();
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
      setFragmentIterator();
    }
    else {
      myDelegate = myFragmentIterator.next();
      myFoldRegion = null;
      myCurrentX = myDelegate.getEndX();
      myCurrentVisualColumn = myDelegate.getEndVisualColumn();
      myCurrentStartLogicalLine = myCurrentEndLogicalLine;
      if (!myFragmentIterator.hasNext()) {
        mySegmentStartOffset = mySegmentEndOffset;
      }
    }
    return myFragment;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  class Fragment {    
    boolean isCollapsedFoldRegion() {
      return myDelegate == null;
    }
    
    int getMinLogicalColumn() {
      return myDelegate == null ? myView.offsetToLogicalPosition(myFoldRegion.getStartOffset()).column : myDelegate.getMinLogicalColumn();
    }
    
    int getMaxLogicalColumn() {
      return myDelegate == null ? myView.offsetToLogicalPosition(myFoldRegion.getEndOffset()).column : myDelegate.getMaxLogicalColumn();
    }
    
    int getStartLogicalColumn() {
      return myDelegate == null ? myView.offsetToLogicalPosition(myFoldRegion.getStartOffset()).column : myDelegate.getStartLogicalColumn();
    }

    int getEndLogicalColumn() {
      return myDelegate == null ? myView.offsetToLogicalPosition(myFoldRegion.getEndOffset()).column : myDelegate.getEndLogicalColumn();
    }
    
    int getStartVisualColumn() {
      return myDelegate == null ? myCurrentVisualColumn - getFoldRegionWidthInColumns(myFoldRegion): myDelegate.getStartVisualColumn();
    }

    int getEndVisualColumn() {
      return myDelegate == null ? myCurrentVisualColumn : myDelegate.getEndVisualColumn();
    }
    
    int getStartLogicalLine() {
      return myCurrentStartLogicalLine;
    }

    int getEndLogicalLine() {
      return myCurrentEndLogicalLine;
    }
    
    float getEndX() {
      return myCurrentX;
    }

    // column is expected to be between minLogicalColumn and maxLogicalColumn for this fragment
    int logicalToVisualColumn(int column) {
      return myDelegate == null ? myCurrentVisualColumn - getFoldRegionWidthInColumns(myFoldRegion) : 
             myDelegate.logicalToVisualColumn(column);
    }

    // column is expected to be between startVisualColumn and endVisualColumn for this fragment
    int visualToLogicalColumn(int column) {
      return myDelegate == null ? (column == myCurrentVisualColumn ? getEndLogicalColumn() : getStartLogicalColumn()) : 
             myDelegate.visualToLogicalColumn(column);
    }

    int xToVisualColumn(float x) {
      return myDelegate == null ? 
             getStartVisualColumn() + 
             getVisualColumnForXInsideFoldRegion(myFoldRegion, x - (myCurrentX - getFoldRegionWidthInPixels(myFoldRegion))) : 
             myDelegate.xToVisualColumn(x);
    }

    float visualColumnToX(int column) {
      return myDelegate == null ? 
             myCurrentX - getFoldRegionWidthInPixels(myFoldRegion) + 
             getXForVisualColumnInsideFoldRegion(myFoldRegion, column - myCurrentVisualColumn + getFoldRegionWidthInColumns(myFoldRegion)) :
             myDelegate.visualColumnToX(column);
    }

    // absolute
    int getStartOffset() {
      return myDelegate == null ? myFoldRegion.getStartOffset() : 
             myDelegate.getStartOffset() + myDocument.getLineStartOffset(myCurrentStartLogicalLine);
    }

    // absolute
    int getEndOffset() {
      return myDelegate == null ? myFoldRegion.getEndOffset() : 
             myDelegate.getEndOffset() + myDocument.getLineStartOffset(myCurrentStartLogicalLine);
    }

    // absolute
    int getMinOffset() {
      return myDelegate == null ? myFoldRegion.getStartOffset() : 
             myDelegate.getMinOffset() + myDocument.getLineStartOffset(myCurrentStartLogicalLine);
    }

    // absolute
    int getMaxOffset() {
      return myDelegate == null ? myFoldRegion.getEndOffset() : 
             myDelegate.getMaxOffset() + myDocument.getLineStartOffset(myCurrentStartLogicalLine);
    }

    // offset is absolute
    float offsetToX(int offset) {
      return myDelegate == null ?
             myCurrentX - getFoldRegionWidthInPixels(myFoldRegion) + getXForOffsetInsideFoldRegion(myFoldRegion, offset) :
             myDelegate.offsetToX(offset - myDocument.getLineStartOffset(myCurrentStartLogicalLine));
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

    // columns are visual (relative to fragment's start)
    void draw(Graphics2D g, float x, float y, int startRelativeColumn, int endRelativeColumn) {
      if (myDelegate == null) {
        for (LineLayout.VisualFragment fragment : myView.getFoldRegionLayout(myFoldRegion).getFragmentsInVisualOrder(x)) {
          int fragmentStart = fragment.getStartVisualColumn();
          int fragmentEnd = fragment.getEndVisualColumn();
          if (fragmentStart < endRelativeColumn && fragmentEnd > startRelativeColumn) {
            fragment.draw(g, fragment.getStartX(), y, 
                          Math.max(0, startRelativeColumn - fragmentStart), Math.min(fragmentEnd, endRelativeColumn) - fragmentStart);
          }
        }
      }
      else {
        myDelegate.draw(g, x, y, startRelativeColumn, endRelativeColumn);
      }
    }
  }
}
