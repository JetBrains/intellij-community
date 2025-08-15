// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.Disposable;
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
import com.intellij.util.IntPair;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class FoldingModelImpl extends InlayModel.SimpleAdapter
  implements FoldingModelEx, FoldingModelInternal, PrioritizedDocumentListener, Dumpable, ModificationTracker {

  /**
   * Do not show default gutter icon for a collapsed region.
   * E.g. if Editor provides another means of expanding the region.
   */
  @ApiStatus.Internal
  public static void hideGutterRendererForCollapsedRegion(@NotNull FoldRegion region) {
    region.putUserData(FoldingKeys.HIDE_GUTTER_RENDERER_FOR_COLLAPSED, Boolean.TRUE);
  }

  private static final Logger LOG = Logger.getInstance(FoldingModelImpl.class);
  private static final Key<SavedCaretPosition> SAVED_CARET_POSITION = Key.create("saved.position.before.folding");
  private static final Key<Boolean> MARK_FOR_UPDATE = Key.create("marked.for.position.update");
  private static final Key<Boolean> DO_NOT_NOTIFY = Key.create("do.not.notify.on.region.disposal");
  private static final HashingStrategy<FoldRegion> OFFSET_BASED_HASHING_STRATEGY = new FoldRegionHashingStrategy();

  private final EditorImpl myEditor;
  private final RangeMarkerTree<FoldRegionImpl> myRegionTree;
  private final FoldRegionsTree myFoldTree;
  private final MultiMap<FoldingGroup, FoldRegion> myGroups = new MultiMap<>();
  private final AtomicLong myExpansionCounter = new AtomicLong();
  private final EditorScrollingPositionKeeper myScrollingPositionKeeper;
  private final List<FoldingListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Set<CustomFoldRegionImpl> myAffectedCustomRegions = new HashSet<>();
  private final AtomicBoolean myIsZombieRaised = new AtomicBoolean();
  private final AtomicBoolean myIsAutoCreatedZombieRaised = new AtomicBoolean();

  private TextAttributes myFoldTextAttributes;
  private boolean myIsFoldingEnabled = true;
  private boolean myIsBatchFoldingProcessing = false;
  private boolean myDoNotCollapseCaret = false;
  private boolean myFoldRegionsProcessed = false;
  private boolean myDocumentChangeProcessed = true;
  private boolean myRegionWidthChanged;
  private boolean myRegionHeightChanged;
  private boolean myGutterRendererChanged;
  private boolean myIsRepaintRequested;
  private boolean myIsComplexDocumentChange;

  FoldingModelImpl(@NotNull EditorImpl editor) {
    myEditor = editor;
    myRegionTree = new MyMarkerTree(editor.getDocument());
    myFoldTree = new MyFoldRegionsTree(myRegionTree);
    myScrollingPositionKeeper = new EditorScrollingPositionKeeper(editor);
    Disposer.register(editor.getDisposable(), myScrollingPositionKeeper);
    updateTextAttributes();
  }

  @Override
  public FoldRegion addFoldRegion(int startOffset, int endOffset, @NotNull String placeholderText) {
    return createFoldRegion(startOffset, endOffset, placeholderText, null, false);
  }

  @Override
  public @Nullable FoldRegion createFoldRegion(
    int startOffset,
    int endOffset,
    @NotNull String placeholder,
    @Nullable FoldingGroup group,
    boolean neverExpands
  ) {
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
    onFoldRegionStateChange(region);
    LOG.assertTrue(region.isValid());
    return region;
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
    onFoldRegionStateChange(region);

    LOG.assertTrue(region.isValid());
    return region;
  }

  @Override
  public void removeFoldRegion(@NotNull FoldRegion region) {
    assertIsDispatchThreadForEditor();
    assertOurRegion(region);

    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
    }
    onFoldProcessingStart();
    ((FoldRegionImpl)region).setExpanded(true, false);
    onFoldRegionStateChange(region);
    beforeFoldRegionRemoved(region);
    region.dispose();
  }

  @Override
  public void clearFoldRegions() {
    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
      return;
    }
    FoldRegion[] regions = getAllFoldRegions();
    if (regions.length > 0) {
      onFoldProcessingStart();
    }
    for (FoldRegion region : regions) {
      if (!region.isExpanded()) onFoldRegionStateChange(region);
      beforeFoldRegionRemoved(region);
      region.dispose();
    }
    doClearFoldRegions();
  }

  @Override
  public @Nullable FoldRegion getFoldRegion(int startOffset, int endOffset) {
    EditorThreading.assertInteractionAllowed();
    return myFoldTree.getRegionAt(startOffset, endOffset);
  }

  @Override
  public @NotNull List<FoldRegion> getGroupedRegions(@NotNull FoldingGroup group) {
    return (List<FoldRegion>)myGroups.get(group);
  }

  @Override
  public @NotNull List<@NotNull FoldRegion> getRegionsOverlappingWith(int startOffset, int endOffset) {
    EditorThreading.assertInteractionAllowed();
    return myFoldTree.fetchOverlapping(startOffset, endOffset);
  }

  @Override
  public FoldRegion @NotNull [] getAllFoldRegions() {
    EditorThreading.assertInteractionAllowed();
    return myFoldTree.fetchAllRegions();
  }

  @Override
  public FoldRegion @Nullable [] fetchTopLevel() {
    return myFoldTree.fetchTopLevel();
  }

  @Override
  public @Nullable FoldRegion getCollapsedRegionAtOffset(int offset) {
    return myFoldTree.fetchOutermost(offset);
  }

  @Override
  public int getLastCollapsedRegionBefore(int offset) {
    return myFoldTree.getLastTopLevelIndexBefore(offset);
  }

  @Override
  public boolean intersectsRegion (int startOffset, int endOffset) {
    return myFoldTree.intersectsRegion(startOffset, endOffset);
  }

  @Override
  public boolean isOffsetCollapsed(int offset) {
    EditorThreading.assertInteractionAllowed();
    return getCollapsedRegionAtOffset(offset) != null;
  }

  @Override
  public @Nullable FoldRegion getFoldingPlaceholderAt(@NotNull Point p) {
    return getFoldingPlaceholderAt(new EditorLocation(myEditor, p), false);
  }

  @Override
  public @Nullable TextAttributes getPlaceholderAttributes() {
    return myFoldTextAttributes;
  }

  @Override
  public void runBatchFoldingOperation(@NotNull Runnable operation, boolean allowMovingCaret, boolean keepRelativeCaretPosition) {
    runBatchFoldingOperation(operation, !allowMovingCaret, true, keepRelativeCaretPosition);
  }

  @Override
  public void addListener(@NotNull FoldingListener listener, @NotNull Disposable parentDisposable) {
    myListeners.add(listener);
    Disposer.register(parentDisposable, () -> myListeners.remove(listener));
  }

  @Override
  public void clearDocumentRangesModificationStatus() {
    assertIsDispatchThreadForEditor();
    myFoldTree.clearDocumentRangesModificationStatus();
  }

  @Override
  public boolean hasDocumentRegionChangedFor(@NotNull FoldRegion region) {
    EditorThreading.assertInteractionAllowed();
    return region instanceof FoldRegionImpl && ((FoldRegionImpl)region).hasDocumentRegionChanged();
  }

  @Override
  public boolean isFoldingEnabled() {
    return myIsFoldingEnabled;
  }

  @Override
  public void setFoldingEnabled(boolean isEnabled) {
    assertIsDispatchThreadForEditor();
    myIsFoldingEnabled = isEnabled;
  }

  @Override
  public void rebuild() {
    if (!myEditor.getDocument().isInBulkUpdate()) {
      myFoldTree.rebuild();
    }
  }

  @Override
  public long getModificationCount() {
    return myExpansionCounter.get();
  }

  @Override
  public @NotNull String dumpState() {
    return Arrays.toString(myFoldTree.fetchTopLevel());
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

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.FOLD_MODEL;
  }

  @Override
  public void onUpdated(@NotNull Inlay<?> inlay, int changeFlags) {
    if ((inlay.getPlacement() == Inlay.Placement.ABOVE_LINE || inlay.getPlacement() == Inlay.Placement.BELOW_LINE) &&
        (changeFlags & InlayModel.ChangeFlags.HEIGHT_CHANGED) != 0) {
      myFoldTree.clearCachedInlayValues();
    }
  }

  @Override
  public boolean isInBatchFoldingOperation() {
    return myIsBatchFoldingProcessing;
  }

  @ApiStatus.Internal
  @Override
  public void updateCachedOffsets() {
    myFoldTree.updateCachedOffsets();
  }

  @ApiStatus.Internal
  @Override
  public int getFoldedLinesCountBefore(int offset) {
    if (!myDocumentChangeProcessed && myEditor.getDocument().isInEventsHandling()) {
      // There is a possible case that this method is called on document update before fold regions are recalculated.
      // We return zero in such situations then.
      return 0;
    }
    return myFoldTree.getFoldedLinesCountBefore(offset);
  }

  @ApiStatus.Internal
  @Override
  public int getTotalNumberOfFoldedLines() {
    if (!myDocumentChangeProcessed && myEditor.getDocument().isInEventsHandling()) {
      // There is a possible case that this method is called on document update before fold regions are recalculated.
      // We return zero in such situations then.
      return 0;
    }
    return myFoldTree.getTotalNumberOfFoldedLines();
  }

  /**
   * Returns (prevAdjustment, curAdjustment) pair.
   * Assuming the provided offset is at the start of a visual line, the first value gives adjustment to Y
   * coordinate of that visual line due to custom fold regions located before (above) that line. The second value gives adjustment to the
   * height of that particular visual line (due to the custom fold region it contains (if it does)).
   */
  @ApiStatus.Internal
  @Override
  public @NotNull IntPair getCustomRegionsYAdjustment(int offset, int prevFoldRegionIndex) {
    return myFoldTree.getCustomRegionsYAdjustment(offset, prevFoldRegionIndex);
  }

  @ApiStatus.Internal
  public FoldRegion @Nullable [] fetchVisible() {
    return myFoldTree.fetchVisible();
  }

  @ApiStatus.Internal
  public int getEndOffset(@NotNull FoldingGroup group) {
    List<FoldRegion> regions = getGroupedRegions(group);
    int endOffset = 0;
    for (FoldRegion region : regions) {
      if (region.isValid()) {
        endOffset = Math.max(endOffset, region.getEndOffset());
      }
    }
    return endOffset;
  }

  @ApiStatus.Internal
  public AtomicBoolean getIsZombieRaised() {
    return myIsZombieRaised;
  }

  @ApiStatus.Internal
  public AtomicBoolean getIsAutoCreatedZombieRaised() {
    return myIsAutoCreatedZombieRaised;
  }

  void refreshSettings() {
    updateTextAttributes();

    runBatchFoldingOperation(() ->
      myRegionTree.processAll(region -> {
        if (region instanceof CustomFoldRegion) {
          ((CustomFoldRegion)region).update();
        }
        return true;
      }));
  }

  void onPlaceholderTextChanged(FoldRegionImpl region) {
    if (!myIsBatchFoldingProcessing) {
      LOG.error("Fold regions must be changed inside batchFoldProcessing() only");
    }
    onFoldProcessingStart();
    myEditor.myView.invalidateFoldRegionLayout(region);
    onFoldRegionStateChange(region);
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
          onFoldProcessingEnd(moveCaret, adjustScrollingPosition);
        }
        else {
          update(myRegionWidthChanged, myRegionHeightChanged, myGutterRendererChanged, myIsRepaintRequested);
        }
        myFoldRegionsProcessed = false;
        myRegionWidthChanged = false;
        myRegionHeightChanged = false;
        myGutterRendererChanged = false;
        setRepaintRequested(false);
      }
      myDoNotCollapseCaret = oldDontCollapseCaret;
    }
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

  void removeRegionFromTree(@NotNull FoldRegionImpl region) {
    ThreadingAssertions.assertEventDispatchThread();
    if (!myEditor.getFoldingModel().isInBatchFoldingOperation()) {
      LOG.error("Fold regions must be added or removed inside batchFoldProcessing() only.");
    }
    onFoldProcessingStart();
    myRegionTree.removeInterval(region);
    removeRegionFromGroup(region);
  }

  void dispose() {
    doClearFoldRegions();
    myRegionTree.dispose(myEditor.getDocument());
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
        int savedOffset = myEditor.logicalPositionToOffset(savedPosition.position());

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
    onFoldProcessingStart();
    myExpansionCounter.incrementAndGet();
    ((FoldRegionImpl) region).setExpandedInternal(true);
    if (notify) onFoldRegionStateChange(region);
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
    onFoldProcessingStart();
    ((FoldRegionImpl) region).setExpandedInternal(false);
    if (notify) onFoldRegionStateChange(region);
  }

  int getHeightOfFoldedBlockInlaysBefore(int prevFoldRegionIndex) {
    return myFoldTree.getHeightOfFoldedBlockInlaysBefore(prevFoldRegionIndex);
  }

  int getTotalHeightOfFoldedBlockInlays() {
    return myFoldTree.getTotalHeightOfFoldedBlockInlays();
  }

  FoldRegion @NotNull [] fetchCollapsedAt(int offset) {
    return myFoldTree.fetchCollapsedAt(offset);
  }

  void flushCaretPosition(@NotNull Caret caret) {
    caret.putUserData(SAVED_CARET_POSITION, null);
  }

  void onBulkDocumentUpdateStarted() {
    clearCachedValues();
  }

  void onBulkDocumentUpdateFinished() {
    validateAffectedCustomRegions();
    myFoldTree.rebuild();
  }

  void onCustomFoldRegionPropertiesChange(@NotNull CustomFoldRegion foldRegion, int flags) {
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

  void setRepaintRequested(boolean repaintRequested) {
    myIsRepaintRequested = repaintRequested;
  }

  void setComplexDocumentChange(boolean complexDocumentChange) {
    myIsComplexDocumentChange = complexDocumentChange;
  }

  void addAffectedCustomRegions(CustomFoldRegionImpl customFoldRegion) {
    myAffectedCustomRegions.add(customFoldRegion);
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

  private void updateTextAttributes() {
    myFoldTextAttributes = myEditor.getColorsScheme().getAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES);
  }

  private boolean isOffsetInsideCollapsedRegion(int offset) {
    EditorThreading.assertInteractionAllowed();
    FoldRegion region = getCollapsedRegionAtOffset(offset);
    return region != null && region.getStartOffset() < offset;
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
      ((EditorGutterComponentImpl)myEditor.getGutterComponentEx()).updateSize();
    }
  }

  private void removeRegionFromGroup(@NotNull FoldRegion region) {
    myGroups.remove(region.getGroup(), region);
  }

  private void doClearFoldRegions() {
    myGroups.clear();
    myFoldTree.clear();
  }

  private void onFoldProcessingEnd(boolean moveCaretFromCollapsedRegion, boolean adjustScrollingPosition) {
    clearCachedValues();

    for (FoldingListener listener : myListeners) {
      listener.onFoldProcessingEnd();
    }

    myEditor.updateCaretCursor();
    myEditor.recalculateSizeAndRepaint();
    ((EditorGutterComponentImpl)myEditor.getGutterComponentEx()).updateSize();
    myEditor.getGutterComponentEx().repaint();
    myEditor.invokeDelayedErrorStripeRepaint();

    myEditor.getCaretModel().runBatchCaretOperation(() -> {
      for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
        LogicalPosition positionToUse = null;
        int offsetToUse = -1;

        SavedCaretPosition savedPosition = caret.getUserData(SAVED_CARET_POSITION);
        boolean markedForUpdate = caret.getUserData(MARK_FOR_UPDATE) != null;

        if (ClientId.isLocal(ClientEditorManager.getClientId(myEditor)) &&
            savedPosition != null && savedPosition.isUpToDate(myEditor)) {
          int savedOffset = myEditor.logicalPositionToOffset(savedPosition.position());
          FoldRegion collapsedAtSaved = myFoldTree.fetchOutermost(savedOffset);
          if (collapsedAtSaved == null) {
            positionToUse = savedPosition.position();
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
        }
        else if (caret.hasSelection() && selectionEnd <= myEditor.getDocument().getTextLength()) {
          caret.setSelection(selectionStart, selectionEnd);
        }
      }
    });
    if (adjustScrollingPosition) myScrollingPositionKeeper.restorePosition(true);
  }

  private void clearCachedValues() {
    myFoldTree.clearCachedValues();
  }

  private void validateAffectedCustomRegions() {
    if (myIsComplexDocumentChange) {
      // validate all custom fold regions
      myRegionTree.processAll(r -> {
        if (r instanceof CustomFoldRegionImpl customFoldRegion) {
          addAffectedCustomRegions(customFoldRegion);
        }
        return true;
      });
      setComplexDocumentChange(false);
    }
    for (CustomFoldRegionImpl region : myAffectedCustomRegions) {
      if (region.isValid() && !myFoldTree.checkIfValidToCreate(region.getStartOffset(), region.getEndOffset(), true, region)) {
        myRegionTree.removeInterval(region);
      }
    }
    myAffectedCustomRegions.clear();
  }

  private void onFoldProcessingStart() {
    if (!myFoldRegionsProcessed) {
      for (FoldingListener listener : myListeners) {
        listener.onFoldProcessingStart();
      }
      myFoldRegionsProcessed = true;
    }
  }

  private void onFoldRegionStateChange(@NotNull FoldRegion foldRegion) {
    for (FoldingListener listener : myListeners) {
      listener.onFoldRegionStateChange(foldRegion);
    }
  }

  private void beforeFoldRegionDisposed(@NotNull FoldRegion foldRegion) {
    for (FoldingListener listener : myListeners) {
      listener.beforeFoldRegionDisposed(foldRegion);
    }
  }

  private void beforeFoldRegionRemoved(@NotNull FoldRegion foldRegion) {
    for (FoldingListener listener : myListeners) {
      listener.beforeFoldRegionRemoved(foldRegion);
    }
  }

  @Override
  public String toString() {
    return dumpState();
  }

  private static void assertOurRegion(FoldRegion region) {
    if (!(region instanceof FoldRegionImpl)) {
      throw new IllegalArgumentException("Only regions created by this instance of FoldingModel are accepted");
    }
  }

  private static void assertIsDispatchThreadForEditor() {
    ThreadingAssertions.assertEventDispatchThread();
  }

  private final class MyMarkerTree extends HardReferencingRangeMarkerTree<FoldRegionImpl> {
    private boolean inCollectCall;

    private MyMarkerTree(@NotNull Document document) {
      super(document);
    }

    @Override
    protected int compareEqualStartIntervals(
      @NotNull IntervalNode<FoldRegionImpl> i1,
      @NotNull IntervalNode<FoldRegionImpl> i2
    ) {
      int baseResult = super.compareEqualStartIntervals(i1, i2);
      if (baseResult != 0) {
        return baseResult;
      }
      boolean c1 = i1.isFlagSet(FRNode.CUSTOM_FLAG);
      boolean c2 = i2.isFlagSet(FRNode.CUSTOM_FLAG);
      return c1 == c2 ? 0 : c1 ? 1 : -1;
    }

    @Override
    protected @NotNull RMNode<FoldRegionImpl> createNewNode(
      @NotNull FoldRegionImpl key,
      int start,
      int end,
      boolean greedyToLeft,
      boolean greedyToRight,
      boolean stickingToRight,
      int layer
    ) {
      return new FRNode(this, key, start, end);
    }

    @ApiStatus.Internal
    @Override
    protected void collectAffectedMarkersAndShiftSubtrees(
      @Nullable IntervalNode<FoldRegionImpl> root,
      int start,
      int end,
      int lengthDelta,
      @NotNull List<? super IntervalNode<FoldRegionImpl>> affected
    ) {
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
      int oldLength = end - start;
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

    @ApiStatus.Internal
    @Override
    protected void fireBeforeRemoved(@NotNull FoldRegionImpl markerEx) {
      if (markerEx.getUserData(DO_NOT_NOTIFY) == null) {
        beforeFoldRegionDisposed(markerEx);
      }
    }

    private static @NotNull FoldRegionImpl getRegion(@NotNull IntervalNode<FoldRegionImpl> node) {
      assert node.intervals.size() == 1;
      FoldRegionImpl region = node.intervals.get(0).get();
      assert region != null;
      return region;
    }

    private final class FRNode extends RangeMarkerTree.RMNode<FoldRegionImpl> {
      static final byte CUSTOM_FLAG = STICK_TO_RIGHT_FLAG<<1;

      private FRNode(@NotNull RangeMarkerTree<FoldRegionImpl> rangeMarkerTree, @NotNull FoldRegionImpl key, int start, int end) {
        super(rangeMarkerTree, key, start, end, false, false, false);
        setFlag(CUSTOM_FLAG, key instanceof CustomFoldRegion);
      }

      @Override
      protected void onRemoved() {
        for (Supplier<? extends FoldRegionImpl> getter : intervals) {
          removeRegionFromGroup(getter.get());
        }
      }

      @Override
      protected void addIntervalsFrom(@NotNull IntervalTreeImpl.IntervalNode<FoldRegionImpl> otherNode) {
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

  private final class MyFoldRegionsTree extends FoldRegionsTree {

    MyFoldRegionsTree(@NotNull RangeMarkerTree<FoldRegionImpl> markerTree) {
      super(markerTree);
    }

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
  }

  private record SavedCaretPosition(LogicalPosition position, long docStamp) {

    SavedCaretPosition(Caret caret) {
      this(caret.getLogicalPosition(), caret.getEditor().getDocument().getModificationStamp());
    }

    private boolean isUpToDate(Editor editor) {
      return docStamp == editor.getDocument().getModificationStamp();
    }
  }

  private static final class FoldRegionHashingStrategy implements HashingStrategy<FoldRegion> {

    @Override
    public int hashCode(@Nullable FoldRegion o) {
      return o == null ? 0 : o.getStartOffset() * 31 + o.getEndOffset();
    }

    @Override
    public boolean equals(@Nullable FoldRegion o1, @Nullable FoldRegion o2) {
      return o1 == o2 || (o1 != null && o2 != null && o1.getStartOffset() == o2.getStartOffset() && o1.getEndOffset() == o2.getEndOffset());
    }
  }
}
