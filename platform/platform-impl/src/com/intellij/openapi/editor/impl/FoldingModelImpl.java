// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.util.DocumentEventUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class FoldingModelImpl extends InlayModel.SimpleAdapter
  implements FoldingModelEx, PrioritizedDocumentListener, Dumpable, ModificationTracker {
  private static final Logger LOG = Logger.getInstance(FoldingModelImpl.class);

  public static final Key<Boolean> SELECT_REGION_ON_CARET_NEARBY = Key.create("select.region.on.caret.nearby");

  private static final Key<SavedCaretPosition> SAVED_CARET_POSITION = Key.create("saved.position.before.folding");
  private static final Key<Boolean> MARK_FOR_UPDATE = Key.create("marked.for.position.update");
  private static final Key<Boolean> DO_NOT_NOTIFY = Key.create("do.not.notify.on.region.disposal");

  private final List<FoldingListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private boolean myIsFoldingEnabled;
  private final EditorImpl myEditor;
  private final RangeMarkerTree<FoldRegionImpl> myRegionTree;
  private final FoldRegionsTree myFoldTree;
  private TextAttributes myFoldTextAttributes;
  private boolean myIsBatchFoldingProcessing;
  private boolean myDoNotCollapseCaret;
  private boolean myFoldRegionsProcessed;
  private boolean myRegionWidthChanged;
  private boolean myRegionHeightChanged;
  private boolean myGutterRendererChanged;
  boolean myRepaintRequested;

  private final MultiMap<FoldingGroup, FoldRegion> myGroups = new MultiMap<>();
  private boolean myDocumentChangeProcessed = true;
  boolean myComplexDocumentChange;
  private final AtomicLong myExpansionCounter = new AtomicLong();
  private final EditorScrollingPositionKeeper myScrollingPositionKeeper;

  final Set<CustomFoldRegionImpl> myAffectedCustomRegions = new HashSet<>();

  FoldingModelImpl(@NotNull EditorImpl editor) {
    myEditor = editor;
    myIsFoldingEnabled = true;
    myIsBatchFoldingProcessing = false;
    myDoNotCollapseCaret = false;
    myRegionTree = new MyMarkerTree(editor.getDocument());
    myFoldTree = new FoldRegionsTree(myRegionTree) {
      @Override
      protected boolean isFoldingEnabled() {
        return FoldingModelImpl.this.isFoldingEnabled();
      }

      @Override
      protected boolean hasBlockInlays() {
        return myEditor.getInlayModel().hasBlockElements();
      }

      @Override
      protected int getFoldedBlockInlaysHeight(int foldStartOffset, int foldEndOffset) {
        int sum = 0;
        for (Inlay<?> inlay : myEditor.getInlayModel().getBlockElementsInRange(foldStartOffset, foldEndOffset)) {
          int offset = inlay.getOffset();
          boolean relatedToPrecedingText = inlay.isRelatedToPrecedingText();
          if ((relatedToPrecedingText || offset != foldStartOffset) &&
              (!relatedToPrecedingText || offset != foldEndOffset) &&
              !InlayModelImpl.showWhenFolded(inlay)) {
            sum += inlay.getHeightInPixels();
          }
        }
        return sum;
      }

      @Override
      protected int getLineHeight() {
        return myEditor.getLineHeight();
      }
    };
    myFoldRegionsProcessed = false;

    myScrollingPositionKeeper = new EditorScrollingPositionKeeper(editor);
    Disposer.register(editor.getDisposable(), myScrollingPositionKeeper);

    updateTextAttributes();
  }

  private static final HashingStrategy<FoldRegion> OFFSET_BASED_HASHING_STRATEGY = new HashingStrategy<>() {
    @Override
    public int hashCode(@Nullable FoldRegion o) {
      return o == null ? 0 : o.getStartOffset() * 31 + o.getEndOffset();
    }

    @Override
    public boolean equals(@Nullable FoldRegion o1, @Nullable FoldRegion o2) {
      return o1 == o2 || (o1 != null && o2 != null && o1.getStartOffset() == o2.getStartOffset() && o1.getEndOffset() == o2.getEndOffset());
    }
  };
  @Override
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

  void refreshSettings() {
    updateTextAttributes();

    runBatchFoldingOperation(() -> {
      myRegionTree.processAll(region -> {
        if (region instanceof CustomFoldRegion) {
          ((CustomFoldRegion)region).update();
        }
        return true;
      });
    });
  }

  private void updateTextAttributes() {
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

  void onPlaceholderTextChanged(FoldRegionImpl region) {
    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be changed inside batchFoldProcessing() only");
    }
    notifyListenersOnFoldProcessingStartIfNeeded();
    myEditor.myView.invalidateFoldRegionLayout(region);
    notifyListenersOnFoldRegionStateChange(region);
  }

  private static void assertIsDispatchThreadForEditor() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }
  private static void assertReadAccess() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
  }

  private static void assertOurRegion(FoldRegion region) {
    if (!(region instanceof FoldRegionImpl)) {
      throw new IllegalArgumentException("Only regions created by this instance of FoldingModel are accepted");
    }
  }

  @Override
  public void setFoldingEnabled(boolean isEnabled) {
    assertIsDispatchThreadForEditor();
    myIsFoldingEnabled = isEnabled;
  }

  @Override
  public FoldRegion addFoldRegion(int startOffset, int endOffset, @NotNull String placeholderText) {
    return createFoldRegion(startOffset, endOffset, placeholderText, null, false);
  }

  @Override
  public void runBatchFoldingOperation(@NotNull Runnable operation, boolean allowMovingCaret, boolean keepRelativeCaretPosition) {
    runBatchFoldingOperation(operation, !allowMovingCaret, true, keepRelativeCaretPosition);
  }

  @Override
  public @Nullable CustomFoldRegion addCustomLinesFolding(int startLine, int endLine, @NotNull CustomFoldRegionRenderer renderer) {
    assertIsDispatchThreadForEditor();
    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
      return null;
    }
    Document document = myEditor.getDocument();
    int maxLineNumber = Math.max(0, document.getLineCount() - 1);
    startLine = Math.max(0, Math.min(maxLineNumber, startLine));
    endLine = Math.max(startLine, Math.min(maxLineNumber, endLine));
    int startOffset = document.getLineStartOffset(startLine);
    int endOffset = document.getLineEndOffset(endLine);

    if (!isFoldingEnabled() || startOffset >= endOffset || !myFoldTree.checkIfValidToCreate(startOffset, endOffset, true, null)) {
      return null;
    }

    CustomFoldRegionImpl region = new CustomFoldRegionImpl(myEditor, startOffset, endOffset, renderer);
    myRegionTree.addInterval(region, startOffset, endOffset, false, false, false, 0);
    LOG.assertTrue(region.isValid());

    collapseFoldRegion(region, false);
    if (region.isExpanded()) { // caret inside region and 'do not collapse caret' flag is active
      region.putUserData(DO_NOT_NOTIFY, Boolean.TRUE);
      myRegionTree.removeInterval(region);
      return null;
    }
    notifyListenersOnFoldRegionStateChange(region);

    LOG.assertTrue(region.isValid());
    return region;
  }

  @Override
  public void runBatchFoldingOperation(@NotNull Runnable operation, boolean moveCaret) {
    runBatchFoldingOperation(operation, false, moveCaret, true);
  }

  void runBatchFoldingOperation(@NotNull Runnable operation,
                                boolean dontCollapseCaret,
                                boolean moveCaret,
                                boolean adjustScrollingPosition) {
    assertIsDispatchThreadForEditor();
    if (myEditor.getInlayModel().isInBatchMode()) LOG.error("Folding operations shouldn't be performed during inlay batch update");

    boolean oldDontCollapseCaret = myDoNotCollapseCaret;
    myDoNotCollapseCaret |= dontCollapseCaret;
    boolean oldBatchFlag = myIsBatchFoldingProcessing;
    if (!oldBatchFlag && adjustScrollingPosition) {
      ((ScrollingModelImpl)myEditor.getScrollingModel()).finishAnimation();
      myScrollingPositionKeeper.savePosition();
    }

    myIsBatchFoldingProcessing = true;
    try {
      operation.run();
    }
    finally {
      if (!oldBatchFlag) {
        myIsBatchFoldingProcessing = false;
        if (myFoldRegionsProcessed) {
          notifyBatchFoldingProcessingDone(moveCaret, adjustScrollingPosition);
        }
        else {
          update(myRegionWidthChanged, myRegionHeightChanged, myGutterRendererChanged, myRepaintRequested);
        }
        myFoldRegionsProcessed = false;
        myRegionWidthChanged = false;
        myRegionHeightChanged = false;
        myGutterRendererChanged = false;
        myRepaintRequested = false;
      }
      myDoNotCollapseCaret = oldDontCollapseCaret;
    }
  }

  private void update(boolean widthChanged, boolean heightChanged, boolean gutterMarkChanged, boolean repaintRequested) {
    if (heightChanged) {
      updateCachedOffsets();
      myEditor.getCaretModel().updateVisualPosition();
    }
    if (widthChanged || heightChanged) {
      myEditor.recalculateSizeAndRepaint();
    }
    else if (repaintRequested) {
      myEditor.getContentComponent().repaint();
    }
    if (gutterMarkChanged) {
      myEditor.getGutterComponentEx().updateSize();
    }
  }

  @Override
  public FoldRegion @NotNull [] getAllFoldRegions() {
    assertReadAccess();
    return myFoldTree.fetchAllRegions();
  }

  @Override
  public @NotNull List<@NotNull FoldRegion> getRegionsOverlappingWith(int startOffset, int endOffset) {
    assertReadAccess();
    return myFoldTree.fetchOverlapping(startOffset, endOffset);
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
  public FoldRegion getFoldingPlaceholderAt(@NotNull Point p) {
    return getFoldingPlaceholderAt(new EditorLocation(myEditor, p), false);
  }

  FoldRegion getFoldingPlaceholderAt(@NotNull EditorLocation location, boolean ignoreCustomRegionWidth) {
    Point p = location.getPoint();
    if (p.y < location.getVisualLineStartY() || p.y >= location.getVisualLineEndY()) {
      // block inlay area
      return null;
    }
    FoldRegion region = location.getCollapsedRegion();
    return !ignoreCustomRegionWidth && region instanceof CustomFoldRegion &&
           p.x >= myEditor.getContentComponent().getInsets().left + ((CustomFoldRegion)region).getWidthInPixels() ? null : region;
  }

  @Override
  public void removeFoldRegion(@NotNull final FoldRegion region) {
    assertIsDispatchThreadForEditor();
    assertOurRegion(region);

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
    }
    notifyListenersOnFoldProcessingStartIfNeeded();
    ((FoldRegionImpl)region).setExpanded(true, false);
    notifyListenersOnFoldRegionStateChange(region);
    notifyListenersOnFoldRegionRemove(region);
    region.dispose();
  }

  void removeRegionFromTree(@NotNull FoldRegionImpl region) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myEditor.getFoldingModel().isInBatchFoldingOperation()) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
    }
    notifyListenersOnFoldProcessingStartIfNeeded();
    myRegionTree.removeInterval(region);
    removeRegionFromGroup(region);
  }

  void removeRegionFromGroup(@NotNull FoldRegion region) {
    myGroups.remove(region.getGroup(), region);
  }

  void dispose() {
    doClearFoldRegions();
    myRegionTree.dispose(myEditor.getDocument());
  }

  @Override
  public void clearFoldRegions() {
    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
      return;
    }
    FoldRegion[] regions = getAllFoldRegions();
    if (regions.length > 0) {
      notifyListenersOnFoldProcessingStartIfNeeded();
    }
    for (FoldRegion region : regions) {
      if (!region.isExpanded()) notifyListenersOnFoldRegionStateChange(region);
      notifyListenersOnFoldRegionRemove(region);
      region.dispose();
    }
    doClearFoldRegions();
  }

  private void doClearFoldRegions() {
    myGroups.clear();
    myFoldTree.clear();
  }

  void expandFoldRegion(@NotNull FoldRegion region, boolean notify) {
    assertIsDispatchThreadForEditor();
    if (region.isExpanded() || region.shouldNeverExpand()) return;

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be collapsed or expanded inside batchFoldProcessing() only.");
    }

    for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
      SavedCaretPosition savedPosition = caret.getUserData(SAVED_CARET_POSITION);
      if (savedPosition != null && savedPosition.isUpToDate(myEditor)) {
        int savedOffset = myEditor.logicalPositionToOffset(savedPosition.position);

        FoldRegion[] allCollapsed = myFoldTree.fetchCollapsedAt(savedOffset);
        if (allCollapsed.length == 1 && allCollapsed[0] == region) {
          caret.putUserData(MARK_FOR_UPDATE, Boolean.TRUE);
        }
      }
      else if (caret.getOffset() == region.getStartOffset()) {
        caret.putUserData(MARK_FOR_UPDATE, Boolean.TRUE);
        caret.putUserData(SAVED_CARET_POSITION, new SavedCaretPosition(caret));
      }
    }
    notifyListenersOnFoldProcessingStartIfNeeded();
    myExpansionCounter.incrementAndGet();
    ((FoldRegionImpl) region).setExpandedInternal(true);
    if (notify) notifyListenersOnFoldRegionStateChange(region);
  }

  void collapseFoldRegion(@NotNull FoldRegion region, boolean notify) {
    assertIsDispatchThreadForEditor();
    if (!region.isExpanded()) return;

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be collapsed or expanded inside batchFoldProcessing() only.");
    }

    List<Caret> carets = myEditor.getCaretModel().getAllCarets();
    if (myDoNotCollapseCaret) {
      for (Caret caret : carets) {
        if (FoldRegionsTree.containsStrict(region, caret.getOffset())) return;
      }
    }
    for (Caret caret : carets) {
      if (FoldRegionsTree.containsStrict(region, caret.getOffset())) {
        SavedCaretPosition savedPosition = caret.getUserData(SAVED_CARET_POSITION);
        if (savedPosition == null || !savedPosition.isUpToDate(myEditor)) {
          caret.putUserData(SAVED_CARET_POSITION, new SavedCaretPosition(caret));
        }
      }
    }
    notifyListenersOnFoldProcessingStartIfNeeded();
    ((FoldRegionImpl) region).setExpandedInternal(false);
    if (notify) notifyListenersOnFoldRegionStateChange(region);
  }

  private void notifyBatchFoldingProcessingDone(boolean moveCaretFromCollapsedRegion, boolean adjustScrollingPosition) {
    clearCachedValues();

    for (FoldingListener listener : myListeners) {
      listener.onFoldProcessingEnd();
    }

    myEditor.updateCaretCursor();
    myEditor.recalculateSizeAndRepaint();
    myEditor.getGutterComponentEx().updateSize();
    myEditor.getGutterComponentEx().repaint();
    myEditor.invokeDelayedErrorStripeRepaint();

    myEditor.getCaretModel().runBatchCaretOperation(() -> {
      for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
        LogicalPosition positionToUse = null;
        int offsetToUse = -1;

        SavedCaretPosition savedPosition = caret.getUserData(SAVED_CARET_POSITION);
        boolean markedForUpdate = caret.getUserData(MARK_FOR_UPDATE) != null;

        if (savedPosition != null && savedPosition.isUpToDate(myEditor)) {
          int savedOffset = myEditor.logicalPositionToOffset(savedPosition.position);
          FoldRegion collapsedAtSaved = myFoldTree.fetchOutermost(savedOffset);
          if (collapsedAtSaved == null) {
            positionToUse = savedPosition.position;
          }
          else {
            offsetToUse = collapsedAtSaved.getPlaceholderText().isEmpty() && !(collapsedAtSaved instanceof CustomFoldRegion)
                          ? collapsedAtSaved.getEndOffset() // old-style rendered docs
                          : collapsedAtSaved.getStartOffset();
          }
        }

        if ((markedForUpdate || moveCaretFromCollapsedRegion) && caret.isUpToDate()) {
          if (offsetToUse >= 0) {
            caret.moveToOffset(offsetToUse);
          }
          else if (positionToUse != null) {
            caret.moveToLogicalPosition(positionToUse);
          }
          else {
            ((CaretImpl)caret).updateVisualPosition();
          }
        }

        caret.putUserData(SAVED_CARET_POSITION, savedPosition);
        caret.putUserData(MARK_FOR_UPDATE, null);

        int selectionStart = caret.getSelectionStart();
        int selectionEnd = caret.getSelectionEnd();
        if (isOffsetInsideCollapsedRegion(selectionStart) || isOffsetInsideCollapsedRegion(selectionEnd)) {
          caret.removeSelection();
        } else if (selectionStart < myEditor.getDocument().getTextLength()) {
          caret.setSelection(selectionStart, selectionEnd);
        }
      }
    });
    if (adjustScrollingPosition) myScrollingPositionKeeper.restorePosition(true);
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

  @ApiStatus.Internal
  public void updateCachedOffsets() {
    myFoldTree.updateCachedOffsets();
  }

  @ApiStatus.Internal
  public int getFoldedLinesCountBefore(int offset) {
    if (!myDocumentChangeProcessed && myEditor.getDocument().isInEventsHandling()) {
      // There is a possible case that this method is called on document update before fold regions are recalculated.
      // We return zero in such situations then.
      return 0;
    }
    return myFoldTree.getFoldedLinesCountBefore(offset);
  }

  int getTotalNumberOfFoldedLines() {
    if (!myDocumentChangeProcessed && myEditor.getDocument().isInEventsHandling()) {
      // There is a possible case that this method is called on document update before fold regions are recalculated.
      // We return zero in such situations then.
      return 0;
    }
    return myFoldTree.getTotalNumberOfFoldedLines();
  }

  int getHeightOfFoldedBlockInlaysBefore(int prevFoldRegionIndex) {
    return myFoldTree.getHeightOfFoldedBlockInlaysBefore(prevFoldRegionIndex);
  }

  int getTotalHeightOfFoldedBlockInlays() {
    return myFoldTree.getTotalHeightOfFoldedBlockInlays();
  }

  /**
   * Returns an array of two values. Assuming provided offset is at the start of a visual line, the first value gives adjustment to Y
   * coordinate of that visual line due to custom fold regions located before (above) that line. The second value gives adjustment to the
   * height of that particular visual line (due to custom fold region it contains, if it does).
   */
  @ApiStatus.Internal
  public int[] getCustomRegionsYAdjustment(int offset, int prevFoldRegionIndex) {
    return myFoldTree.getCustomRegionsYAdjustment(offset, prevFoldRegionIndex);
  }

  @Override
  public FoldRegion @Nullable [] fetchTopLevel() {
    return myFoldTree.fetchTopLevel();
  }

  FoldRegion @NotNull [] fetchCollapsedAt(int offset) {
    return myFoldTree.fetchCollapsedAt(offset);
  }

  @Override
  public boolean intersectsRegion (int startOffset, int endOffset) {
    return myFoldTree.intersectsRegion(startOffset, endOffset);
  }

  FoldRegion @Nullable [] fetchVisible() {
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

  void flushCaretPosition(@NotNull Caret caret) {
    caret.putUserData(SAVED_CARET_POSITION, null);
  }

  void onBulkDocumentUpdateStarted() {
    clearCachedValues();
  }

  void clearCachedValues() {
    myFoldTree.clearCachedValues();
  }

  void onBulkDocumentUpdateFinished() {
    validateAffectedCustomRegions();
    myFoldTree.rebuild();
  }

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent event) {
    if (myIsBatchFoldingProcessing) LOG.error("Document changes are not allowed during batch folding update");
    myDocumentChangeProcessed = false;
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent event) {
    try {
      if (event.getDocument().isInBulkUpdate()) return;
      validateAffectedCustomRegions();
      if (DocumentEventUtil.isMoveInsertion(event)) {
        myFoldTree.rebuild();
      }
      else {
        updateCachedOffsets();
      }
    }
    finally {
      myDocumentChangeProcessed = true;
    }
  }

  private void validateAffectedCustomRegions() {
    if (myComplexDocumentChange) {
      // validate all custom fold regions
      myRegionTree.processAll(r -> {
        if (r instanceof CustomFoldRegionImpl) {
          myAffectedCustomRegions.add((CustomFoldRegionImpl)r);
        }
        return true;
      });
      myComplexDocumentChange = false;
    }
    for (CustomFoldRegionImpl region : myAffectedCustomRegions) {
      if (region.isValid() && !myFoldTree.checkIfValidToCreate(region.getStartOffset(), region.getEndOffset(), true, region)) {
        myRegionTree.removeInterval(region);
      }
    }
    myAffectedCustomRegions.clear();
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.FOLD_MODEL;
  }

  @Override
  public void onUpdated(@NotNull Inlay inlay, int changeFlags) {
    if ((inlay.getPlacement() == Inlay.Placement.ABOVE_LINE || inlay.getPlacement() == Inlay.Placement.BELOW_LINE) &&
        (changeFlags & InlayModel.ChangeFlags.HEIGHT_CHANGED) != 0) {
      myFoldTree.clearCachedInlayValues();
    }
  }

  @Nullable
  @Override
  public FoldRegion createFoldRegion(int startOffset,
                                     int endOffset,
                                     @NotNull String placeholder,
                                     @Nullable FoldingGroup group,
                                     boolean neverExpands) {
    assertIsDispatchThreadForEditor();
    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
      return null;
    }
    if (!isFoldingEnabled() ||
        startOffset >= endOffset ||
        neverExpands && group != null ||
        DocumentUtil.isInsideCharacterPair(myEditor.getDocument(), startOffset) ||
        DocumentUtil.isInsideCharacterPair(myEditor.getDocument(), endOffset) ||
        !myFoldTree.checkIfValidToCreate(startOffset, endOffset, false, null)) {
      return null;
    }

    FoldRegionImpl region = new FoldRegionImpl(myEditor, startOffset, endOffset, placeholder, group, neverExpands);
    myRegionTree.addInterval(region, startOffset, endOffset, false, false, false, 0);
    LOG.assertTrue(region.isValid());
    if (neverExpands) {
      collapseFoldRegion(region, false);
      if (region.isExpanded()) {
        region.putUserData(DO_NOT_NOTIFY, Boolean.TRUE);
        myRegionTree.removeInterval(region);
        return null;
      }
    }
    myFoldRegionsProcessed = true;
    if (group != null) {
      myGroups.putValue(group, region);
    }
    notifyListenersOnFoldRegionStateChange(region);
    LOG.assertTrue(region.isValid());
    return region;
  }

  @Override
  public void addListener(@NotNull final FoldingListener listener, @NotNull Disposable parentDisposable) {
    myListeners.add(listener);
    Disposer.register(parentDisposable, () -> myListeners.remove(listener));
  }

  private void notifyListenersOnFoldProcessingStartIfNeeded() {
    if (!myFoldRegionsProcessed) {
      for (FoldingListener listener : myListeners) {
        listener.onFoldProcessingStart();
      }
      myFoldRegionsProcessed = true;
    }
  }

  private void notifyListenersOnFoldRegionStateChange(@NotNull FoldRegion foldRegion) {
    for (FoldingListener listener : myListeners) {
      listener.onFoldRegionStateChange(foldRegion);
    }
  }

  void notifyListenersOnPropertiesChange(@NotNull CustomFoldRegion foldRegion, int flags) {
    for (FoldingListener listener : myListeners) {
      listener.onCustomFoldRegionPropertiesChange(foldRegion, flags);
    }
    boolean widthChanged = (flags & FoldingListener.ChangeFlags.WIDTH_CHANGED) != 0;
    boolean heightChanged = (flags & FoldingListener.ChangeFlags.HEIGHT_CHANGED) != 0;
    boolean gutterMarkChanged = (flags & FoldingListener.ChangeFlags.GUTTER_ICON_PROVIDER_CHANGED) != 0;
    if (myIsBatchFoldingProcessing) {
      myRegionWidthChanged |= widthChanged;
      myRegionHeightChanged |= heightChanged;
      myGutterRendererChanged |= gutterMarkChanged;
    }
    else {
      update(widthChanged, heightChanged, gutterMarkChanged, false);
    }
  }

  private void notifyListenersOnFoldRegionDisposal(@NotNull FoldRegion foldRegion) {
    for (FoldingListener listener : myListeners) {
      listener.beforeFoldRegionDisposed(foldRegion);
    }
  }

  private void notifyListenersOnFoldRegionRemove(@NotNull FoldRegion foldRegion) {
    for (FoldingListener listener : myListeners) {
      listener.beforeFoldRegionRemoved(foldRegion);
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

  @TestOnly
  void validateState() {
    Document document = myEditor.getDocument();
    if (document.isInBulkUpdate()) return;

    FoldRegion[] allFoldRegions = getAllFoldRegions();
    boolean[] invisibleRegions = new boolean[allFoldRegions.length];
    for (int i = 0; i < allFoldRegions.length; i++) {
      FoldRegion r1 = allFoldRegions[i];
      int r1s = r1.getStartOffset();
      int r1e = r1.getEndOffset();
      boolean r1c = r1 instanceof CustomFoldRegion;
      LOG.assertTrue(r1.isValid() &&
                     !DocumentUtil.isInsideCharacterPair(document, r1s) &&
                     !DocumentUtil.isInsideCharacterPair(document, r1e),
                     "Invalid region");
      if (r1c) {
        LOG.assertTrue(!r1.isExpanded(), "Expanded custom region");
        LOG.assertTrue(r1s == DocumentUtil.getLineStartOffset(r1s, document) && r1e == DocumentUtil.getLineEndOffset(r1e, document),
                       "Wrong custom region position");
      }
      for (int j = i + 1; j < allFoldRegions.length; j++) {
        FoldRegion r2 = allFoldRegions[j];
        int r2s = r2.getStartOffset();
        int r2e = r2.getEndOffset();
        boolean r2c = r2 instanceof CustomFoldRegion;
        LOG.assertTrue(r1c == r2c &&
                       (r1s < r2s && (r1e <= r2s || r1e >= r2e) ||
                       r1s == r2s && r1e != r2e ||
                       r1s > r2s && r1s < r2e && r1e <= r2e ||
                       r1s >= r2e)
                       || r1c && !r2c && (r1s > r2e || r1e < r2s || r1s > r2s && r1e < r2e || r1s <= r2s && r1e >= r2e)
                       || !r1c && r2c && (r1s > r2e || r1e < r2s || r2s > r1s && r2e < r1e || r2s <= r1s && r2e >= r1e),
                       "Disallowed relative position of regions");
        if (!r1.isExpanded() && r1s <= r2s && r1e >= r2e && (r1s != r2s || r1e != r2e || r1c)) invisibleRegions[j] = true;
        if (!r2.isExpanded() && r2s <= r1s && r2e >= r1e && (r1s != r2s || r1e != r2e || r2c)) invisibleRegions[i] = true;
      }
    }
    Set<FoldRegion> visibleRegions = CollectionFactory.createCustomHashingStrategySet(OFFSET_BASED_HASHING_STRATEGY);
    List<FoldRegion> topLevelRegions = new ArrayList<>();
    for (int i = 0; i < allFoldRegions.length; i++) {
      if (!invisibleRegions[i]) {
        FoldRegion region = allFoldRegions[i];
        LOG.assertTrue(visibleRegions.add(region), "Duplicate visible regions");
        if (!region.isExpanded()) {
          topLevelRegions.add(region);
        }
      }
    }
    topLevelRegions.sort(Comparator.comparingInt(r -> r.getStartOffset()));

    FoldRegion[] actualVisibles = fetchVisible();
    if (actualVisibles != null) {
      for (FoldRegion r : actualVisibles) {
        LOG.assertTrue(visibleRegions.remove(r), "Unexpected visible region");
      }
      LOG.assertTrue(visibleRegions.isEmpty(), "Missing visible region");
    }

    FoldRegion[] actualTopLevels = fetchTopLevel();
    if (actualTopLevels != null) {
      LOG.assertTrue(actualTopLevels.length == topLevelRegions.size(), "Wrong number of top-level regions");
      for (int i = 0; i < actualTopLevels.length; i++) {
        LOG.assertTrue(OFFSET_BASED_HASHING_STRATEGY.equals(actualTopLevels[i], topLevelRegions.get(i)),
                       "Unexpected top-level region");
      }
    }
  }

  private static final class SavedCaretPosition {
    private final LogicalPosition position;
    private final long docStamp;

    private SavedCaretPosition(Caret caret) {
      position = caret.getLogicalPosition();
      docStamp = caret.getEditor().getDocument().getModificationStamp();
    }

    private boolean isUpToDate(Editor editor) {
      return docStamp == editor.getDocument().getModificationStamp();
    }
  }

  private final class MyMarkerTree extends HardReferencingRangeMarkerTree<FoldRegionImpl> {
    private boolean inCollectCall;

    private MyMarkerTree(Document document) {
      super(document);
    }

    @NotNull
    private FoldRegionImpl getRegion(@NotNull IntervalNode<FoldRegionImpl> node) {
      assert node.intervals.size() == 1;
      FoldRegionImpl region = node.intervals.get(0).get();
      assert region != null;
      return region;
    }

    @Override
    protected int compareEqualStartIntervals(@NotNull IntervalNode<FoldRegionImpl> i1,
                                             @NotNull IntervalNode<FoldRegionImpl> i2) {
      int baseResult = super.compareEqualStartIntervals(i1, i2);
      if (baseResult != 0) {
        return baseResult;
      }
      boolean c1 = i1.isFlagSet(FRNode.CUSTOM_FLAG);
      boolean c2 = i2.isFlagSet(FRNode.CUSTOM_FLAG);
      return c1 == c2 ? 0 : c1 ? 1 : -1;
    }

    @NotNull
    @Override
    protected RMNode<FoldRegionImpl> createNewNode(@NotNull FoldRegionImpl key,
                                                 int start,
                                                 int end,
                                                 boolean greedyToLeft,
                                                 boolean greedyToRight,
                                                 boolean stickingToRight,
                                                 int layer) {
      return new FRNode(this, key, start, end);
    }

    @Override
    void collectAffectedMarkersAndShiftSubtrees(@Nullable IntervalNode<FoldRegionImpl> root,
                                                   int start, int end, int lengthDelta,
                                                   @NotNull List<? super IntervalNode<FoldRegionImpl>> affected) {
      if (inCollectCall) {
        super.collectAffectedMarkersAndShiftSubtrees(root, start, end, lengthDelta, affected);
        return;
      }
      inCollectCall = true;
      try {
        super.collectAffectedMarkersAndShiftSubtrees(root, start, end, lengthDelta, affected);
      }
      finally {
        inCollectCall = false;
      }
      final int oldLength = end - start;
      if (oldLength > 0 /* document change can cause regions to become equal*/) {
        for (Object o : affected) {
          //noinspection unchecked
          RMNode<FoldRegionImpl> node = (RMNode<FoldRegionImpl>)o;
          FoldRegionImpl region = getRegion(node);
          // region with the largest metric value is kept when several regions become identical after document change
          // we want the largest collapsed region to survive
          region.mySizeBeforeUpdate = region.isExpanded() ? 0 : node.intervalEnd() - node.intervalStart();
        }
      }
    }

    @Override
    void fireBeforeRemoved(@NotNull FoldRegionImpl markerEx, @NotNull Object reason) {
      if (markerEx.getUserData(DO_NOT_NOTIFY) == null) {
        notifyListenersOnFoldRegionDisposal(markerEx);
      }
    }

    private class FRNode extends RangeMarkerTree.RMNode<FoldRegionImpl> {
      static final byte CUSTOM_FLAG = STICK_TO_RIGHT_FLAG<<1;

      private FRNode(@NotNull RangeMarkerTree<FoldRegionImpl> rangeMarkerTree, @NotNull FoldRegionImpl key, int start, int end) {
        super(rangeMarkerTree, key, start, end, false, false, false);
        setFlag(CUSTOM_FLAG, key instanceof CustomFoldRegion);
      }

      @Override
      void onRemoved() {
        for (Supplier<FoldRegionImpl> getter : intervals) {
          removeRegionFromGroup(getter.get());
        }
      }

      @Override
      void addIntervalsFrom(@NotNull IntervalTreeImpl.IntervalNode<FoldRegionImpl> otherNode) {
        FoldRegionImpl region = getRegion(this);
        FoldRegionImpl otherRegion = getRegion(otherNode);
        if (otherRegion.mySizeBeforeUpdate > region.mySizeBeforeUpdate) {
          setNode(region, null);
          removeRegionFromGroup(region);
          removeIntervalInternal(0);
          super.addIntervalsFrom(otherNode);
        }
        else {
          otherNode.setValid(false);
          ((RangeMarkerTree.RMNode<FoldRegionImpl>)otherNode).onRemoved();
        }
      }
    }
  }
}
