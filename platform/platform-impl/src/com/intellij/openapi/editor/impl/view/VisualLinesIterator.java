// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VisualLinesIterator {
  private final EditorImpl myEditor;
  private final Document myDocument;
  private final FoldRegion[] myFoldRegions;
  private final List<? extends SoftWrap> mySoftWraps;

  @NotNull
  private Location myLocation;
  private Location myNextLocation;
  
  public VisualLinesIterator(@NotNull EditorImpl editor, int startVisualLine) {
    myEditor = editor;
    SoftWrapModelImpl softWrapModel = myEditor.getSoftWrapModel();
    myDocument = myEditor.getDocument();
    FoldRegion[] regions = myEditor.getFoldingModel().fetchTopLevel();
    myFoldRegions = regions == null ? FoldRegion.EMPTY_ARRAY : regions;
    mySoftWraps = softWrapModel.getRegisteredSoftWraps();
    myLocation = new Location(startVisualLine);
  }

  public boolean atEnd() {
    return myLocation.atEnd();
  }
  
  public void advance() {
    checkEnd();
    if (myNextLocation == null) {
      myLocation.advance();
    }
    else {
      myLocation = myNextLocation;
      myNextLocation = null;
    }
  }

  private void setNextLocation() {
    if (myNextLocation == null) {
      myNextLocation = myLocation.clone();
      myNextLocation.advance();
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

  public int getStartLogicalLine() {
    checkEnd();
    return myLocation.logicalLine - 1;
  }

  public int getEndLogicalLine() {
    checkEnd();
    setNextLocation();
    return myNextLocation.atEnd() ? myDocument.getLineCount() - 1
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
    return myLocation.y;
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

  private void checkEnd() {
    if (atEnd()) throw new IllegalStateException("Iteration finished");
  }

  private final class Location implements Cloneable {
    private final int lineHeight; // editor's line height
    private int visualLine;       // current visual line
    private int offset;           // start offset of the current visual line
    private int logicalLine = 1;  // 1 + start logical line of the current visual line
    private int foldRegion;       // index of the first folding region on current or following visual lines
    private int softWrap;         // index of the first soft wrap after the start of current visual line
    private int y;                // y coordinate of visual line's top
    
    private Location(int startVisualLine) {
      lineHeight = myEditor.getLineHeight();
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
        y = myEditor.visibleLineToY(startVisualLine);
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
      y += lineHeight;
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
