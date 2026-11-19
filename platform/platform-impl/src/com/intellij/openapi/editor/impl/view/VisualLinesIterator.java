// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.ex.DocumentText;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.SoftWrapModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapEx;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.editor.impl.InlayModelImpl.showWhenFolded;

/**
 * If one needs to perform some actions for a continuous range of visual lines, using this class would be most surely faster than
 * calculating various values (e.g. start/end offsets) for all visual lines in the range individually.
 */
public final class VisualLinesIterator {
  private static final int UNSET = -1;

  private final DocumentText myDocument;
  private final EditorView myView;
  private final int myLineHeight;

  private final FoldRegion[] myFoldRegions;

  private final InlayModel myInlayModel;
  private final List<Inlay<?>> myInlaysAbove = new ArrayList<>();
  private final List<Inlay<?>> myInlaysBelow = new ArrayList<>();
  private boolean myInlaysSet;

  private final List<? extends SoftWrapEx> mySoftWraps;

  private @NotNull Location myLocation;
  private Location myNextLocation;
  private int y = UNSET; // y coordinate of visual line's top

  public VisualLinesIterator(@NotNull EditorImpl editor, int startVisualLine) {
    this(editor.getDocument().getDocumentText(), editor.getView(), startVisualLine);
  }

  @ApiStatus.Internal
  public VisualLinesIterator(@NotNull DocumentText document, @NotNull EditorView view, int startVisualLine) {
    myDocument = document;
    myView = view;
    myInlayModel = myView.getInlayModel();
    FoldingModelEx foldingModel = myView.getFoldingModel();
    SoftWrapModelEx softWrapModel = myView.getSoftWrapModel();
    myFoldRegions = ObjectUtils.notNull(foldingModel.fetchTopLevel(), FoldRegion.EMPTY_ARRAY);
    mySoftWraps = myView.getSoftWrapModel().getRegisteredSoftWrapsEx();
    myLineHeight = myView.getLineHeight();
    int visualLine, offset, logicalLine, foldRegion, softWrap;
    if (startVisualLine < 0 || myView.getVisibleLineCount(document) <= startVisualLine) {
      visualLine = 0;
      offset = -1;
      logicalLine = 1;
      foldRegion = 0;
      softWrap = 0;
    } else if (startVisualLine > 0) {
      visualLine = startVisualLine;
      offset = myView.visualLineToOffset(startVisualLine);
      logicalLine = myDocument.lineNumber(offset) + 1;
      foldRegion = foldingModel.getLastCollapsedRegionBefore(offset) + 1;
      softWrap = softWrapModel.getSoftWrapIndex(offset) + 1;
      if (softWrap <= 0) {
        softWrap = -softWrap;
      }
    } else {
      visualLine = 0;
      offset = 0;
      logicalLine = 1;
      foldRegion = 0;
      softWrap = 0;
    }
    myLocation = new Location(visualLine, offset, logicalLine, foldRegion, softWrap);
  }

  public boolean atEnd() {
    return myLocation.atEnd();
  }

  public void advance() {
    checkEnd();
    if (y != UNSET) {
      y += EditorUtil.getTotalInlaysHeight(getBlockInlaysBelow());
      y += getLineHeight();
    }
    if (myNextLocation == null) {
      myLocation.advance();
    } else {
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
    if (myNextLocation.atEnd()) {
      return myDocument.length();
    }
    if (myNextLocation.softWrap == myLocation.softWrap) {
      return myDocument.lineEndOffset(myNextLocation.logicalLine - 2);
    }
    return myNextLocation.offset;
  }

  public int getDisplayedLogicalLine() {
    checkEnd();
    int foldIndex = myLocation.foldRegion;
    if (foldIndex < myFoldRegions.length) {
      FoldRegion foldRegion = myFoldRegions[foldIndex];
      if (foldRegion.getPlaceholderText().isEmpty() &&
          foldRegion.getStartOffset() == myLocation.offset) {
        return myDocument.lineNumber(foldRegion.getEndOffset());
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
    if (myNextLocation.atEnd()) {
      return Math.max(0, myDocument.lineCount() - 1);
    }
    return myNextLocation.logicalLine - (myNextLocation.softWrap == myLocation.softWrap ? 2 : 1);
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
      y = myView.visualLineToY(myLocation.visualLine);
    }
    return y;
  }

  public boolean startsWithSoftWrap() {
    checkEnd();
    return myLocation.softWrap > 0 &&
           myLocation.softWrap <= mySoftWraps.size() &&
           mySoftWraps.get(myLocation.softWrap - 1).getStart() == myLocation.offset;
  }

  public boolean endsWithSoftWrap() {
    checkEnd();
    return myLocation.softWrap < mySoftWraps.size() &&
           mySoftWraps.get(myLocation.softWrap).getStart() == getVisualLineEndOffset();
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

  public @Nullable CustomFoldRegion getCustomFoldRegion() {
    checkEnd();
    int foldIndex = myLocation.foldRegion;
    if (foldIndex < myFoldRegions.length) {
      FoldRegion foldRegion = myFoldRegions[foldIndex];
      if (foldRegion instanceof CustomFoldRegion customRegion) {
        if (foldRegion.getStartOffset() == myLocation.offset) {
          return customRegion;
        }
      }
    }
    return null;
  }

  public boolean isCustomFoldRegionLine() {
    return getCustomFoldRegion() != null;
  }

  public int getLineHeight() {
    CustomFoldRegion region = getCustomFoldRegion();
    return region == null ? myLineHeight : region.getHeightInPixels();
  }

  /**
   * Gives the soft wrap at the start of the visual line, or null when the visual line does not start with a soft wrap.
   */
  @ApiStatus.Internal
  public @Nullable SoftWrapEx getStartSoftWrap() {
    if (startsWithSoftWrap()) {
      return mySoftWraps.get(myLocation.softWrap - 1);
    }
    return null;
  }

  /**
   * Gives the soft wrap at the end of the visual line, or null when the visual line does not end with a soft wrap.
   */
  @ApiStatus.Internal
  public @Nullable SoftWrapEx getEndSoftWrap() {
    if (endsWithSoftWrap()) {
      return mySoftWraps.get(myLocation.softWrap);
    }
    return null;
  }

  private void checkEnd() {
    if (atEnd()) {
      throw new IllegalStateException("Iteration finished");
    }
  }

  private void setNextLocation() {
    if (myNextLocation == null) {
      myNextLocation = myLocation.copy();
      myNextLocation.advance();
    }
  }

  private void setInlays() {
    if (myInlaysSet) {
      return;
    }
    myInlaysSet = true;
    myInlaysAbove.clear();
    myInlaysBelow.clear();
    setNextLocation();
    int endOffset = myNextLocation.atEnd() ? myDocument.length() : myNextLocation.offset - 1;
    List<Inlay<?>> inlays = myInlayModel.getBlockElementsInRange(myLocation.offset, endOffset);
    for (Inlay<?> inlay : inlays) {
      int inlayOffset = inlay.getOffset() - (inlay.isRelatedToPrecedingText() ? 0 : 1);
      int foldIndex = myLocation.foldRegion;
      while (foldIndex < myFoldRegions.length &&
             myFoldRegions[foldIndex].getEndOffset() <= inlayOffset) {
        foldIndex++;
      }
      if (foldIndex < myFoldRegions.length &&
          myFoldRegions[foldIndex].getStartOffset() <= inlayOffset &&
          !showWhenFolded(inlay)) {
        continue;
      }
      if (inlay.getPlacement() == Inlay.Placement.ABOVE_LINE) {
        myInlaysAbove.add(inlay);
      } else {
        myInlaysBelow.add(inlay);
      }
    }
  }

  private final class Location {
    private int visualLine;   // current visual line
    private int offset;       // start offset of the current visual line
    private int logicalLine;  // 1 + start logical line of the current visual line
    private int foldRegion;   // index of the first folding region on current or following visual lines
    private int softWrap;     // index of the first soft wrap after the start of current visual line

    Location(
      int visualLine,
      int offset,
      int logicalLine,
      int foldRegion,
      int softWrap
    ) {
      this.visualLine = visualLine;
      this.offset = offset;
      this.logicalLine = logicalLine;
      this.foldRegion = foldRegion;
      this.softWrap = softWrap;
    }

    void advance() {
      int nextWrapOffset = getNextSoftWrapOffset();
      offset = getNextVisualLineStartOffset(nextWrapOffset);
      if (offset == Integer.MAX_VALUE) {
        offset = -1;
      } else if (offset == nextWrapOffset) {
        softWrap++;
      }
      visualLine++;
      while (foldRegion < myFoldRegions.length &&
             myFoldRegions[foldRegion].getStartOffset() < offset) {
        foldRegion++;
      }
    }

    boolean atEnd() {
      return offset == -1;
    }

    @NotNull Location copy() {
      return new Location(visualLine, offset, logicalLine, foldRegion, softWrap);
    }

    private int getNextSoftWrapOffset() {
      if (softWrap < mySoftWraps.size()) {
        return mySoftWraps.get(softWrap).getStart();
      }
      return Integer.MAX_VALUE;
    }

    private int getNextVisualLineStartOffset(int nextWrapOffset) {
      while (logicalLine < myDocument.lineCount()) {
        int lineStartOffset = myDocument.lineStartOffset(logicalLine);
        if (lineStartOffset > nextWrapOffset) {
          return nextWrapOffset;
        }
        logicalLine++;
        if (!isCollapsed(lineStartOffset)) {
          return lineStartOffset;
        }
      }
      return nextWrapOffset;
    }

    private boolean isCollapsed(int offset) {
      while (foldRegion < myFoldRegions.length) {
        FoldRegion region = myFoldRegions[foldRegion];
        if (offset <= region.getStartOffset()) {
          return false;
        }
        if (offset <= region.getEndOffset()) {
          return true;
        }
        foldRegion++;
      }
      return false;
    }
  }
}
