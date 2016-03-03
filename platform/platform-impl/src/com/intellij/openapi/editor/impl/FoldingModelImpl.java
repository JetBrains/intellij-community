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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 4, 2002
 * Time: 8:27:13 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class FoldingModelImpl implements FoldingModelEx, PrioritizedInternalDocumentListener, Dumpable, ModificationTracker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorFoldingModelImpl");
  
  private static final Key<LogicalPosition> SAVED_CARET_POSITION = Key.create("saved.position.before.folding");
  private static final Key<Boolean> MARK_FOR_UPDATE = Key.create("marked.for.position.update");

  private final List<FoldingListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private boolean myIsFoldingEnabled;
  private final EditorImpl myEditor;
  private final FoldRegionsTree myFoldTree;
  private TextAttributes myFoldTextAttributes;
  private boolean myIsBatchFoldingProcessing;
  private boolean myDoNotCollapseCaret;
  private boolean myFoldRegionsProcessed;

  private int mySavedCaretShift;
  private final MultiMap<FoldingGroup, FoldRegion> myGroups = new MultiMap<FoldingGroup, FoldRegion>();
  private boolean myDocumentChangeProcessed = true;
  private final AtomicLong myExpansionCounter = new AtomicLong();

  public FoldingModelImpl(EditorImpl editor) {
    myEditor = editor;
    myIsFoldingEnabled = true;
    myIsBatchFoldingProcessing = false;
    myDoNotCollapseCaret = false;
    myFoldTree = new FoldRegionsTree() {
      @Override
      protected boolean isFoldingEnabled() {
        return FoldingModelImpl.this.isFoldingEnabled();
      }
    };
    myFoldRegionsProcessed = false;
    refreshSettings();
  }

  @NotNull
  public List<FoldRegion> getGroupedRegions(@NotNull FoldingGroup group) {
    return (List<FoldRegion>)myGroups.get(group);
  }

  @Override
  public void clearDocumentRangesModificationStatus() {
    assertIsDispatchThreadForEditor();
    myFoldTree.clearDocumentRangesModificationStatus();
  }

  @Override
  public boolean hasDocumentRegionChangedFor(@NotNull FoldRegion region) {
    assertReadAccess();
    return region instanceof FoldRegionImpl && ((FoldRegionImpl)region).hasDocumentRegionChanged();
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

  @Override
  public boolean isFoldingEnabled() {
    return myIsFoldingEnabled;
  }

  @Override
  public boolean isOffsetCollapsed(int offset) {
    assertReadAccess();
    return getCollapsedRegionAtOffset(offset) != null;
  }

  private boolean isOffsetInsideCollapsedRegion(int offset) {
    assertReadAccess();
    FoldRegion region = getCollapsedRegionAtOffset(offset);
    return region != null && region.getStartOffset() < offset;
  }

  private static void assertIsDispatchThreadForEditor() {
    ApplicationManagerEx.getApplicationEx().assertIsDispatchThread();
  }
  private static void assertReadAccess() {
    ApplicationManagerEx.getApplicationEx().assertReadAccessAllowed();
  }

  @Override
  public void setFoldingEnabled(boolean isEnabled) {
    assertIsDispatchThreadForEditor();
    myIsFoldingEnabled = isEnabled;
  }

  @Override
  public FoldRegion addFoldRegion(int startOffset, int endOffset, @NotNull String placeholderText) {
    FoldRegion region = createFoldRegion(startOffset, endOffset, placeholderText, null, false);
    if (region == null) return null;
    if (!addFoldRegion(region)) {
      region.dispose();
      return null;
    }

    return region;
  }

  @Override
  public boolean addFoldRegion(@NotNull final FoldRegion region) {
    assertIsDispatchThreadForEditor();
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
      }
      for (FoldingListener listener : myListeners) {
        listener.onFoldRegionStateChange(region);
      }
      return true;
    }

    return false;
  }

  @Override
  public void runBatchFoldingOperation(@NotNull Runnable operation) {
    runBatchFoldingOperation(operation, false, true);
  }

  @Override
  public void runBatchFoldingOperation(@NotNull Runnable operation, boolean moveCaret) {
    runBatchFoldingOperation(operation, false, moveCaret);
  }

  private void runBatchFoldingOperation(final Runnable operation, final boolean dontCollapseCaret, final boolean moveCaret) {
    assertIsDispatchThreadForEditor();
    boolean oldDontCollapseCaret = myDoNotCollapseCaret;
    myDoNotCollapseCaret |= dontCollapseCaret;
    boolean oldBatchFlag = myIsBatchFoldingProcessing;
    if (!oldBatchFlag) {
      ((ScrollingModelImpl)myEditor.getScrollingModel()).finishAnimation();
      mySavedCaretShift = myEditor.visibleLineToY(myEditor.getCaretModel().getVisualPosition().line) - myEditor.getScrollingModel().getVerticalScrollOffset();
    }

    myIsBatchFoldingProcessing = true;
    try {
      operation.run();
    } finally {
      if (!oldBatchFlag) {
        if (myFoldRegionsProcessed) {
          notifyBatchFoldingProcessingDone(moveCaret);
          myFoldRegionsProcessed = false;
        }
        myIsBatchFoldingProcessing = false;
      }
      myDoNotCollapseCaret = oldDontCollapseCaret;
    }
  }

  @Override
  public void runBatchFoldingOperationDoNotCollapseCaret(@NotNull final Runnable operation) {
    runBatchFoldingOperation(operation, true, true);
  }

  /**
   * Disables caret position adjustment after batch folding operation is finished.
   * Should be called from inside batch operation runnable.
   */
  public void flushCaretShift() {
    mySavedCaretShift = -1;
  }

  @Override
  @NotNull
  public FoldRegion[] getAllFoldRegions() {
    assertReadAccess();
    return myFoldTree.fetchAllRegions();
  }

  @Override
  @Nullable
  public FoldRegion getCollapsedRegionAtOffset(int offset) {
    return myFoldTree.fetchOutermost(offset);
  }

  @Nullable
  @Override
  public FoldRegion getFoldRegion(int startOffset, int endOffset) {
    assertReadAccess();
    return myFoldTree.getRegionAt(startOffset, endOffset);
  }

  @Override
  @Nullable
  public FoldRegion getFoldingPlaceholderAt(Point p) {
    assertReadAccess();
    LogicalPosition pos = myEditor.xyToLogicalPosition(p);
    int line = pos.line;

    if (line >= myEditor.getDocument().getLineCount()) return null;

    int offset = myEditor.logicalPositionToOffset(pos);

    return myFoldTree.fetchOutermost(offset);
  }

  @Override
  public void removeFoldRegion(@NotNull final FoldRegion region) {
    assertIsDispatchThreadForEditor();

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
    }

    region.setExpanded(true);
    final FoldingGroup group = region.getGroup();
    if (group != null) {
      myGroups.remove(group, region);
    }

    myFoldTree.removeRegion(region);
    myFoldRegionsProcessed = true;
    region.dispose();
  }

  public void dispose() {
    doClearFoldRegions();
  }

  @Override
  public void clearFoldRegions() {
    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
      return;
    }
    if (myEditor.myUseNewRendering) {
      FoldRegion[] regions = getAllFoldRegions();
      for (FoldRegion region : regions) {
        if (!region.isExpanded()) {
          notifyListenersOnFoldRegionStateChange(region);
          myFoldRegionsProcessed = true;
        }
      }
    }
    doClearFoldRegions();
  }

  public void doClearFoldRegions() {
    myGroups.clear();
    myFoldTree.clear();
  }

  public void expandFoldRegion(FoldRegion region) {
    assertIsDispatchThreadForEditor();
    if (region.isExpanded() || region.shouldNeverExpand()) return;

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be collapsed or expanded inside batchFoldProcessing() only.");
    }

    for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
      LogicalPosition savedPosition = caret.getUserData(SAVED_CARET_POSITION);
      if (savedPosition != null) {
        int savedOffset = myEditor.logicalPositionToOffset(savedPosition);

        FoldRegion[] allCollapsed = myFoldTree.fetchCollapsedAt(savedOffset);
        if (allCollapsed.length == 1 && allCollapsed[0] == region) {
          caret.putUserData(MARK_FOR_UPDATE, Boolean.TRUE);
        }
      }
    }

    myFoldRegionsProcessed = true;
    myExpansionCounter.incrementAndGet();
    ((FoldRegionImpl) region).setExpandedInternal(true);
    notifyListenersOnFoldRegionStateChange(region);
  }

  public void collapseFoldRegion(FoldRegion region) {
    assertIsDispatchThreadForEditor();
    if (!region.isExpanded()) return;

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be collapsed or expanded inside batchFoldProcessing() only.");
    }

    List<Caret> carets = myEditor.getCaretModel().getAllCarets();
    for (Caret caret : carets) {
      LogicalPosition caretPosition = caret.getLogicalPosition();
      int caretOffset = myEditor.logicalPositionToOffset(caretPosition);
      
      if (FoldRegionsTree.contains(region, caretOffset)) {
        if (myDoNotCollapseCaret) return;
      }
    }
    for (Caret caret : carets) {
      LogicalPosition caretPosition = caret.getLogicalPosition();
      int caretOffset = myEditor.logicalPositionToOffset(caretPosition);
      
      if (FoldRegionsTree.contains(region, caretOffset)) {
        if (caret.getUserData(SAVED_CARET_POSITION) == null) {
          caret.putUserData(SAVED_CARET_POSITION, caretPosition.withoutVisualPositionInfo());
        }
      }
    }

    myFoldRegionsProcessed = true;
    ((FoldRegionImpl) region).setExpandedInternal(false);
    notifyListenersOnFoldRegionStateChange(region);
  }

  private void notifyBatchFoldingProcessingDone(final boolean moveCaretFromCollapsedRegion) {
    rebuild();

    for (FoldingListener listener : myListeners) {
      listener.onFoldProcessingEnd();
    }

    myEditor.updateCaretCursor();
    myEditor.recalculateSizeAndRepaint();
    myEditor.getGutterComponentEx().updateSize();
    myEditor.getGutterComponentEx().repaint();

    for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
      // There is a possible case that caret position is already visual position aware. But visual position depends on number of folded
      // logical lines as well, hence, we can't be sure that target logical position defines correct visual position because fold
      // regions have just changed. Hence, we use 'raw' logical position instead.
      LogicalPosition caretPosition = caret.getLogicalPosition().withoutVisualPositionInfo();
      int caretOffset = myEditor.logicalPositionToOffset(caretPosition);
      int selectionStart = caret.getSelectionStart();
      int selectionEnd = caret.getSelectionEnd();

      LogicalPosition positionToUse = null;
      int offsetToUse = -1;

      FoldRegion collapsed = myFoldTree.fetchOutermost(caretOffset);
      LogicalPosition savedPosition = caret.getUserData(SAVED_CARET_POSITION);
      boolean markedForUpdate = caret.getUserData(MARK_FOR_UPDATE) != null;
      
      if (savedPosition != null) {
        int savedOffset = myEditor.logicalPositionToOffset(savedPosition);
        FoldRegion collapsedAtSaved = myFoldTree.fetchOutermost(savedOffset);
        if (collapsedAtSaved == null) {
          positionToUse = savedPosition;
        }
        else {
          offsetToUse = collapsedAtSaved.getStartOffset();
        }
      }

      if (collapsed != null && positionToUse == null) {
        positionToUse = myEditor.offsetToLogicalPosition(collapsed.getStartOffset());
      }

      if ((markedForUpdate || moveCaretFromCollapsedRegion) && caret.isUpToDate()) {
        if (offsetToUse >= 0) {
          caret.moveToOffset(offsetToUse);
        }
        else if (positionToUse != null) {
          caret.moveToLogicalPosition(positionToUse);
        }
        else {
          caret.moveToLogicalPosition(caretPosition);
        }
      }

      caret.putUserData(SAVED_CARET_POSITION, savedPosition);
      caret.putUserData(MARK_FOR_UPDATE, null);

      if (isOffsetInsideCollapsedRegion(selectionStart) || isOffsetInsideCollapsedRegion(selectionEnd)) {
        caret.removeSelection();
      } else if (selectionStart < myEditor.getDocument().getTextLength()) {
        caret.setSelection(selectionStart, selectionEnd);
      }
    }

    if (mySavedCaretShift > 0) {
      final ScrollingModel scrollingModel = myEditor.getScrollingModel();
      scrollingModel.disableAnimation();
      scrollingModel.scrollVertically(myEditor.visibleLineToY(myEditor.getCaretModel().getVisualPosition().line) - mySavedCaretShift);
      scrollingModel.enableAnimation();
    }
  }

  @Override
  public void rebuild() {
    if (!myEditor.getDocument().isInBulkUpdate()) {
      myFoldTree.rebuild();
    }
  }

  public boolean isInBatchFoldingOperation() {
    return myIsBatchFoldingProcessing;
  }

  private void updateCachedOffsets() {
    myFoldTree.updateCachedOffsets();
  }

  public int getFoldedLinesCountBefore(int offset) {
    if (!myDocumentChangeProcessed && myEditor.getDocument().isInEventsHandling()) {
      // There is a possible case that this method is called on document update before fold regions are recalculated.
      // We return zero in such situations then. 
      return 0;
    }
    return myFoldTree.getFoldedLinesCountBefore(offset);
  }

  @Override
  @Nullable
  public FoldRegion[] fetchTopLevel() {
    return myFoldTree.fetchTopLevel();
  }

  public FoldRegion[] fetchCollapsedAt(int offset) {
    return myFoldTree.fetchCollapsedAt(offset);
  }

  @Override
  public boolean intersectsRegion (int startOffset, int endOffset) {
    return myFoldTree.intersectsRegion(startOffset, endOffset);
  }

  public FoldRegion[] fetchVisible() {
    return myFoldTree.fetchVisible();
  }

  @Override
  public int getLastCollapsedRegionBefore(int offset) {
    return myFoldTree.getLastTopLevelIndexBefore(offset);
  }

  @Override
  public TextAttributes getPlaceholderAttributes() {
    return myFoldTextAttributes;
  }

  public void flushCaretPosition() {
    for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
      caret.putUserData(SAVED_CARET_POSITION, null);
    }
  }

  void onBulkDocumentUpdateStarted() {
    myFoldTree.clearCachedValues();
  }

  void onBulkDocumentUpdateFinished() {
    myFoldTree.rebuild();
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    myDocumentChangeProcessed = false;
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    try {
      if (!((DocumentEx)event.getDocument()).isInBulkUpdate()) {
        updateCachedOffsets();
      }
    }
    finally {
      myDocumentChangeProcessed = true;
    }
  }

  @Override
  public void moveTextHappened(int start, int end, int base) {
    if (!myEditor.getDocument().isInBulkUpdate()) {
      myFoldTree.rebuild();
    }
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.FOLD_MODEL;
  }

  @Override
  public FoldRegion createFoldRegion(int startOffset,
                                     int endOffset,
                                     @NotNull String placeholder,
                                     @Nullable FoldingGroup group,
                                     boolean neverExpands) {
    FoldRegionImpl region = new FoldRegionImpl(myEditor, startOffset, endOffset, placeholder, group, neverExpands);
    LOG.assertTrue(region.isValid());
    return region;
  }

  @Override
  public void addListener(@NotNull final FoldingListener listener, @NotNull Disposable parentDisposable) {
    myListeners.add(listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        myListeners.remove(listener);
      }
    });
  }

  private void notifyListenersOnFoldRegionStateChange(@NotNull FoldRegion foldRegion) {
    for (FoldingListener listener : myListeners) {
      listener.onFoldRegionStateChange(foldRegion);
    }
  }

  @NotNull
  @Override
  public String dumpState() {
    return Arrays.toString(myFoldTree.fetchTopLevel());
  }

  @Override
  public String toString() {
    return dumpState();
  }

  @Override
  public long getModificationCount() {
    return myExpansionCounter.get();
  }
}
