// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.editor.impl.InlayModelImpl.showWhenFolded;

/**
 * If one needs to perform some actions for a continuous range of visual lines, using this class would be most surely faster than
 * calculating various values (e.g. start/end offsets) for all visual lines in the range individually.
 */
public final class VisualLinesIterator {
  private static final int UNSET = -1;

  private final EditorImpl myEditor;
  private final Document myDocument;
  private final FoldRegion[] myFoldRegions;
  private final List<? extends SoftWrap> mySoftWraps;
  private final int myLineHeight;

  private final List<Inlay<?>> myInlaysAbove = new ArrayList<>();
  private final List<Inlay<?>> myInlaysBelow = new ArrayList<>();
  private boolean myInlaysSet;

  @NotNull
  private Location myLocation;
  private Location myNextLocation;
  private int y = UNSET; // y coordinate of visual line's top

  public VisualLinesIterator(@NotNull EditorImpl editor, int startVisualLine) {
    myEditor = editor;
    SoftWrapModelImpl softWrapModel = myEditor.getSoftWrapModel();
    myDocument = myEditor.getDocument();
    FoldRegion[] regions = myEditor.getFoldingModel().fetchTopLevel();
    myFoldRegions = regions == null ? FoldRegion.EMPTY_ARRAY : regions;
    mySoftWraps = softWrapModel.getRegisteredSoftWraps();
    myLineHeight = myEditor.getLineHeight();
    myLocation = new Location(startVisualLine);
  }

  public boolean atEnd() {
    return myLocation.atEnd();
  }

  public void advance() {
    checkEnd();
    if (y != UNSET) {
      y += EditorUtil.getTotalInlaysHeight(getBlockInlaysBelow());
      y += myLineHeight;
    }
    if (myNextLocation == null) {
      myLocation.advance();
    }
    else {
      myLocation = myNextLocation;
      myNextLocation = null;
    }
    myInlaysSet = false;
    if (y != UNSET && !atEnd()) {
      y += EditorUtil.getTotalInlaysHeight(getBlockInlaysAbove());
    }
  }

  public int getVisualLine() {
    checkEnd();
    return myLocation.visualLine;
  }

  public int getVisualLineStartOffset() {
    checkEnd();
    return myLocation.offset;
  }

  public int getVisualLineEndOffset() {
    checkEnd();
    setNextLocation();
    return myNextLocation.atEnd() ? myDocument.getTextLength() :
           myNextLocation.softWrap == myLocation.softWrap ? myDocument.getLineEndOffset(myNextLocation.logicalLine - 2) :
           myNextLocation.offset;
  }

  public int getDisplayedLogicalLine() {
    checkEnd();
    int foldIndex = myLocation.foldRegion;
    if (foldIndex < myFoldRegions.length) {
      FoldRegion foldRegion = myFoldRegions[foldIndex];
      if (foldRegion.getPlaceholderText().isEmpty() && foldRegion.getStartOffset() == myLocation.offset) {
        return myDocument.getLineNumber(foldRegion.getEndOffset());
      }
    }
    return myLocation.logicalLine - 1;
  }

  public int getStartLogicalLine() {
    checkEnd();
    return myLocation.logicalLine - 1;
  }

  public int getEndLogicalLine() {
    checkEnd();
    setNextLocation();
    return myNextLocation.atEnd() ? Math.max(0, myDocument.getLineCount() - 1)
                                  : myNextLocation.logicalLine - (myNextLocation.softWrap == myLocation.softWrap ? 2 : 1);
  }

  public int getStartOrPrevWrapIndex() {
    checkEnd();
    return myLocation.softWrap - 1;
  }

  public int getStartFoldingIndex() {
    checkEnd();
    return myLocation.foldRegion;
  }

  public int getY() {
    checkEnd();
    if (y == UNSET) {
      y = myEditor.visualLineToY(myLocation.visualLine);
    }
    return y;
  }

  public boolean startsWithSoftWrap() {
    checkEnd();
    return myLocation.softWrap > 0 && myLocation.softWrap <= mySoftWraps.size() &&
           mySoftWraps.get(myLocation.softWrap - 1).getStart() == myLocation.offset;
  }

  public boolean endsWithSoftWrap() {
    checkEnd();
    return myLocation.softWrap < mySoftWraps.size() && mySoftWraps.get(myLocation.softWrap).getStart() == getVisualLineEndOffset();
  }

  public List<Inlay<?>> getBlockInlaysAbove() {
    checkEnd();
    setInlays();
    return myInlaysAbove;
  }

  public List<Inlay<?>> getBlockInlaysBelow() {
    checkEnd();
    setInlays();
    return myInlaysBelow;
  }

  private void checkEnd() {
    if (atEnd()) throw new IllegalStateException("Iteration finished");
  }

  private void setNextLocation() {
    if (myNextLocation == null) {
      myNextLocation = myLocation.clone();
      myNextLocation.advance();
    }
  }

  private void setInlays() {
    if (myInlaysSet) return;
    myInlaysSet = true;
    myInlaysAbove.clear();
    myInlaysBelow.clear();
    setNextLocation();
    List<Inlay<?>> inlays = myEditor.getInlayModel()
      .getBlockElementsInRange(myLocation.offset, myNextLocation.atEnd() ? myDocument.getTextLength() : myNextLocation.offset - 1);
    for (Inlay<?> inlay : inlays) {
      int inlayOffset = inlay.getOffset() - (inlay.isRelatedToPrecedingText() ? 0 : 1);
      int foldIndex = myLocation.foldRegion;
      while (foldIndex < myFoldRegions.length && myFoldRegions[foldIndex].getEndOffset() <= inlayOffset) foldIndex++;
      if (foldIndex < myFoldRegions.length && myFoldRegions[foldIndex].getStartOffset() <= inlayOffset && !showWhenFolded(inlay)) continue;
      (inlay.getPlacement() == Inlay.Placement.ABOVE_LINE ? myInlaysAbove : myInlaysBelow).add(inlay);
    }
  }

  private final class Location implements Cloneable {
    private int visualLine;       // current visual line
    private int offset;           // start offset of the current visual line
    private int logicalLine = 1;  // 1 + start logical line of the current visual line
    private int foldRegion;       // index of the first folding region on current or following visual lines
    private int softWrap;         // index of the first soft wrap after the start of current visual line

    private Location(int startVisualLine) {
      if (startVisualLine < 0 || startVisualLine >= myEditor.getVisibleLineCount()) {
        offset = -1;
      }
      else if (startVisualLine > 0) {
        visualLine = startVisualLine;
        offset = myEditor.visualLineStartOffset(startVisualLine);
        logicalLine = myDocument.getLineNumber(offset) + 1;
        softWrap = myEditor.getSoftWrapModel().getSoftWrapIndex(offset) + 1;
        if (softWrap <= 0) {
          softWrap = -softWrap;
        }
        foldRegion = myEditor.getFoldingModel().getLastCollapsedRegionBefore(offset) + 1;
      }
    }

    private void advance() {
      int nextWrapOffset = getNextSoftWrapOffset();
      offset = getNextVisualLineStartOffset(nextWrapOffset);
      if (offset == Integer.MAX_VALUE) {
        offset = -1;
      }
      else if (offset == nextWrapOffset) {
        softWrap++;
      }
      visualLine++;
      while (foldRegion < myFoldRegions.length && myFoldRegions[foldRegion].getStartOffset() < offset) foldRegion++;
    }

    private int getNextSoftWrapOffset() {
      return softWrap < mySoftWraps.size() ? mySoftWraps.get(softWrap).getStart() : Integer.MAX_VALUE;
    }

    private int getNextVisualLineStartOffset(int nextWrapOffset) {
      while (logicalLine < myDocument.getLineCount()) {
        int lineStartOffset = myDocument.getLineStartOffset(logicalLine);
        if (lineStartOffset > nextWrapOffset) return nextWrapOffset;
        logicalLine++;
        if (!isCollapsed(lineStartOffset)) return lineStartOffset;
      }
      return nextWrapOffset;
    }

    private boolean isCollapsed(int offset) {
      while (foldRegion < myFoldRegions.length) {
        FoldRegion region = myFoldRegions[foldRegion];
        if (offset <= region.getStartOffset()) return false;
        if (offset <= region.getEndOffset()) return true;
        foldRegion++;
      }
      return false;
    }

    private boolean atEnd() {
      return offset == -1;
    }

    @Override
    protected Location clone() {
      try {
        return (Location)super.clone();
      }
      catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
