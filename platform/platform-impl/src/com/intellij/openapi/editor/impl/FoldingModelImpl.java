/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 4, 2002
 * Time: 8:27:13 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class FoldingModelImpl implements FoldingModelEx, PrioritizedDocumentListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorFoldingModelImpl");

  private final Set<FoldingListener> myListeners = new CopyOnWriteArraySet<FoldingListener>();

  private boolean myIsFoldingEnabled;
  private final EditorImpl myEditor;
  private final FoldRegionsTree myFoldTree;
  private TextAttributes myFoldTextAttributes;
  private boolean myIsBatchFoldingProcessing;
  private boolean myDoNotCollapseCaret;
  private boolean myFoldRegionsProcessed;

  private int mySavedCaretX;
  private int mySavedCaretY;
  private int mySavedCaretShift;
  private boolean myCaretPositionSaved;
  private final MultiMap<FoldingGroup, FoldRegion> myGroups = new MultiMap<FoldingGroup, FoldRegion>();

  public FoldingModelImpl(EditorImpl editor) {
    myEditor = editor;
    myIsFoldingEnabled = true;
    myIsBatchFoldingProcessing = false;
    myDoNotCollapseCaret = false;
    myFoldTree = new FoldRegionsTree() {
      protected boolean isFoldingEnabled() {
        return FoldingModelImpl.this.isFoldingEnabled();
      }

      protected boolean isBatchFoldingProcessing() {
        return myIsBatchFoldingProcessing;
      }
    };
    myFoldRegionsProcessed = false;
    refreshSettings();
  }

  @NotNull
  public List<FoldRegion> getGroupedRegions(@NotNull FoldingGroup group) {
    return (List<FoldRegion>)myGroups.get(group);
  }

  @NotNull
  public FoldRegion getFirstRegion(@NotNull FoldingGroup group, FoldRegion child) {
    final List<FoldRegion> regions = getGroupedRegions(group);
    if (regions.isEmpty()) {
      final boolean inAll = Arrays.asList(getAllFoldRegions()).contains(child);
      throw new AssertionError("Folding group without children; the known child is in all: " + inAll);
    }

    FoldRegion main = regions.get(0);
    for (int i = 1; i < regions.size(); i++) {
      FoldRegion region = regions.get(i);
      if (main.getStartOffset() > region.getStartOffset()) {
        main = region;
      }
    }
    return main;
  }

  public int getEndOffset(@NotNull FoldingGroup group) {
    final List<FoldRegion> regions = getGroupedRegions(group);
    int endOffset = 0;
    for (FoldRegion region : regions) {
      if (region.isValid()) {
        endOffset = Math.max(endOffset, region.getEndOffset());
      }
    }
    return endOffset;
  }

  public void refreshSettings() {
    myFoldTextAttributes = myEditor.getColorsScheme().getAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES);
  }

  public boolean isFoldingEnabled() {
    return myIsFoldingEnabled;
  }

  public boolean isOffsetCollapsed(int offset) {
    assertReadAccess();
    return getCollapsedRegionAtOffset(offset) != null;
  }

  private void assertIsDispatchThread() {
    ApplicationManagerEx.getApplicationEx().assertIsDispatchThread(myEditor.getComponent());
  }
  private static void assertReadAccess() {
    ApplicationManagerEx.getApplicationEx().assertReadAccessAllowed();
  }

  public void setFoldingEnabled(boolean isEnabled) {
    assertIsDispatchThread();
    myIsFoldingEnabled = isEnabled;
  }

  public FoldRegion addFoldRegion(int startOffset, int endOffset, @NotNull String placeholderText) {
    FoldRegion region = createFoldRegion(startOffset, endOffset, placeholderText, null);
    return region == null || addFoldRegion(region) ? region : null;
  }

  public boolean addFoldRegion(@NotNull final FoldRegion region) {
    assertIsDispatchThread();
    if (!isFoldingEnabled()) {
      return false;
    }
    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
      return false;
    }

    myFoldRegionsProcessed = true;
    if (myFoldTree.addRegion(region)) {
      final FoldingGroup group = region.getGroup();
      if (group != null) {
        myGroups.putValue(group, region);
        for (FoldingListener listener : myListeners) {
          listener.onFoldRegionStateChange(region);
        }
      }
      return true;
    }

    return false;
  }

  public void runBatchFoldingOperation(@NotNull Runnable operation) {
    runBatchFoldingOperation(operation, false);
  }

  private void runBatchFoldingOperation(final Runnable operation, final boolean dontCollapseCaret) {
    assertIsDispatchThread();
    boolean oldDontCollapseCaret = myDoNotCollapseCaret;
    myDoNotCollapseCaret |= dontCollapseCaret;
    boolean oldBatchFlag = myIsBatchFoldingProcessing;
    if (!oldBatchFlag) {
      mySavedCaretShift = myEditor.visibleLineNumberToYPosition(myEditor.getCaretModel().getVisualPosition().line) - myEditor.getScrollingModel().getVerticalScrollOffset();
    }

    myIsBatchFoldingProcessing = true;
    myFoldTree.myCachedLastIndex = -1;
    operation.run();
    myFoldTree.myCachedLastIndex = -1;

    if (!oldBatchFlag) {
      if (myFoldRegionsProcessed) {
        notifyBatchFoldingProcessingDone();
        myFoldRegionsProcessed = false;
      }
      myIsBatchFoldingProcessing = false;
    }
    myDoNotCollapseCaret = oldDontCollapseCaret;
  }

  public void runBatchFoldingOperationDoNotCollapseCaret(@NotNull final Runnable operation) {
    runBatchFoldingOperation(operation, true);
  }

  public void flushCaretShift() {
    mySavedCaretShift = -1;
  }

  @NotNull
  public FoldRegion[] getAllFoldRegions() {
    assertReadAccess();
    return myFoldTree.fetchAllRegions();
  }

  public FoldRegion getCollapsedRegionAtOffset(int offset) {
    return myFoldTree.fetchOutermost(offset);
  }

  int getLastTopLevelIndexBefore (int offset) {
    return myFoldTree.getLastTopLevelIndexBefore(offset);
  }

  public FoldRegion getFoldingPlaceholderAt(Point p) {
    assertReadAccess();
    LogicalPosition pos = myEditor.xyToLogicalPosition(p);
    int line = pos.line;

    if (line >= myEditor.getDocument().getLineCount()) return null;

    //leftmost folded block position
    if (myEditor.xyToVisualPosition(p).equals(myEditor.logicalToVisualPosition(pos))) return null;

    int offset = myEditor.logicalPositionToOffset(pos);

    return myFoldTree.fetchOutermost(offset);
  }

  public void removeFoldRegion(@NotNull final FoldRegion region) {
    assertIsDispatchThread();

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
    }

    region.setExpanded(true);
    final FoldingGroup group = region.getGroup();
    if (group != null) {
      myGroups.removeValue(group, region);
      }
    myFoldTree.removeRegion(region);
    myFoldRegionsProcessed = true;
  }

  public void expandFoldRegion(FoldRegion region) {
    assertIsDispatchThread();
    if (region.isExpanded()) return;

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be collapsed or expanded inside batchFoldProcessing() only.");
    }

    if (myCaretPositionSaved) {
      int savedOffset = myEditor.logicalPositionToOffset(new LogicalPosition(mySavedCaretY, mySavedCaretX));

      FoldRegion[] allCollapsed = myFoldTree.fetchCollapsedAt(savedOffset);
      if (allCollapsed.length == 1 && allCollapsed[0] == region) {
        LogicalPosition pos = new LogicalPosition(mySavedCaretY, mySavedCaretX);
        myEditor.getCaretModel().moveToLogicalPosition(pos);
      }
    }

    myFoldRegionsProcessed = true;
    ((FoldRegionImpl) region).setExpandedInternal(true);
    notifyListenersOnFoldRegionStateChange(region);
  }

  public void collapseFoldRegion(FoldRegion region) {
    assertIsDispatchThread();
    if (!region.isExpanded()) return;

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be collapsed or expanded inside batchFoldProcessing() only.");
    }

    LogicalPosition caretPosition = myEditor.getCaretModel().getLogicalPosition();

    int caretOffset = myEditor.logicalPositionToOffset(caretPosition);

    if (FoldRegionsTree.contains(region, caretOffset)) {
      if (myDoNotCollapseCaret) return;

      if (!myCaretPositionSaved) {
        mySavedCaretX = caretPosition.column;
        mySavedCaretY = caretPosition.line;
        myCaretPositionSaved = true;
      }
    }

    int selectionStart = myEditor.getSelectionModel().getSelectionStart();
    int selectionEnd = myEditor.getSelectionModel().getSelectionEnd();

    if (FoldRegionsTree.contains(region, selectionStart-1) || FoldRegionsTree.contains(region, selectionEnd)) myEditor.getSelectionModel().removeSelection();

    myFoldRegionsProcessed = true;
    ((FoldRegionImpl) region).setExpandedInternal(false);
    notifyListenersOnFoldRegionStateChange(region);
  }

  private void notifyBatchFoldingProcessingDone() {
    try {
      doNotifyBatchFoldingProcessingDone();
    } finally {
      for (FoldingListener listener : myListeners) {
        listener.onFoldProcessingEnd();
      }
    }
  }

  private void doNotifyBatchFoldingProcessingDone() {
    myFoldTree.rebuild();

    myEditor.updateCaretCursor();
    myEditor.recalculateSizeAndRepaint();
    if (myEditor.getGutterComponentEx().isFoldingOutlineShown()) {
      myEditor.getGutterComponentEx().repaint();
    }

    LogicalPosition caretPosition = myEditor.getCaretModel().getLogicalPosition();
    int caretOffset = myEditor.logicalPositionToOffset(caretPosition);
    boolean hasBlockSelection = myEditor.getSelectionModel().hasBlockSelection();
    int selectionStart = myEditor.getSelectionModel().getSelectionStart();
    int selectionEnd = myEditor.getSelectionModel().getSelectionEnd();

    int column = -1;
    int line = -1;
    int offsetToUse = -1;

    FoldRegion collapsed = myFoldTree.fetchOutermost(caretOffset);
    if (myCaretPositionSaved) {
      int savedOffset = myEditor.logicalPositionToOffset(new LogicalPosition(mySavedCaretY, mySavedCaretX));
      FoldRegion collapsedAtSaved = myFoldTree.fetchOutermost(savedOffset);
      if (collapsedAtSaved == null) {
        column = mySavedCaretX;
        line = mySavedCaretY;
      }
      else {
        offsetToUse = collapsedAtSaved.getStartOffset();
      }
    }

    if (collapsed != null && column == -1) {
      line = collapsed.getDocument().getLineNumber(collapsed.getStartOffset());
      column = myEditor.offsetToLogicalPosition(collapsed.getStartOffset()).column;
    }

    boolean oldCaretPositionSaved = myCaretPositionSaved;

    if (offsetToUse >= 0) {
      myEditor.getCaretModel().moveToOffset(offsetToUse);
    }
    else if (column != -1) {
      myEditor.getCaretModel().moveToLogicalPosition(new LogicalPosition(line, column));
    }
    else {
      myEditor.getCaretModel().moveToLogicalPosition(caretPosition);
    }

    myCaretPositionSaved = oldCaretPositionSaved;

    if (!hasBlockSelection) {
      myEditor.getSelectionModel().setSelection(selectionStart, selectionEnd);
    }

    if (mySavedCaretShift > 0) {
      myEditor.getScrollingModel().disableAnimation();
      int scrollTo = myEditor.visibleLineNumberToYPosition(myEditor.getCaretModel().getVisualPosition().line) - mySavedCaretShift;
      myEditor.getScrollingModel().scrollVertically(scrollTo);
      myEditor.getScrollingModel().enableAnimation();
    }
  }

  public void rebuild() {
    myFoldTree.rebuild();
  }

  private void updateCachedOffsets() {
    myFoldTree.updateCachedOffsets();
  }

  public int getFoldedLinesCountBefore(int offset) {
    return myFoldTree.getFoldedLinesCountBefore(offset);
  }

  public FoldRegion[] fetchTopLevel() {
    return myFoldTree.fetchTopLevel();
  }

  public FoldRegion fetchOutermost(int offset) {
    return myFoldTree.fetchOutermost(offset);
  }

  public FoldRegion[] fetchCollapsedAt(int offset) {
    return myFoldTree.fetchCollapsedAt(offset);
  }

  public boolean intersectsRegion (int startOffset, int endOffset) {
    return myFoldTree.intersectsRegion(startOffset, endOffset);
  }

  public FoldRegion[] fetchVisible() {
    return myFoldTree.fetchVisible();
  }

  public int getLastCollapsedRegionBefore(int offset) {
    return myFoldTree.getLastTopLevelIndexBefore(offset);
  }

  public TextAttributes getPlaceholderAttributes() {
    return myFoldTextAttributes;
  }

  public void flushCaretPosition() {
    myCaretPositionSaved = false;
  }

  public void beforeDocumentChange(DocumentEvent event) {
  }

  public void documentChanged(DocumentEvent event) {
    if (((DocumentEx)event.getDocument()).isInBulkUpdate()) {
      myFoldTree.clear();
    } else {
      updateCachedOffsets();
    }
  }

  public int getPriority() {
    return EditorDocumentPriorities.FOLD_MODEL;
  }

  public FoldRegion createFoldRegion(int startOffset, int endOffset, @NotNull String placeholder, FoldingGroup group) {
    FoldRegionImpl region = new FoldRegionImpl(myEditor, startOffset, endOffset, placeholder, group);
    region.registerInDocument();
    LOG.assertTrue(region.isValid());
    return region;
  }

  @Override
  public boolean addListener(@NotNull FoldingListener listener) {
    return myListeners.add(listener);
  }

  @Override
  public boolean removeListener(@NotNull FoldingListener listener) {
    return myListeners.remove(listener);
  }

  private void notifyListenersOnFoldRegionStateChange(@NotNull FoldRegion foldRegion) {
    for (FoldingListener listener : myListeners) {
      listener.onFoldRegionStateChange(foldRegion);
    }
  }
}
