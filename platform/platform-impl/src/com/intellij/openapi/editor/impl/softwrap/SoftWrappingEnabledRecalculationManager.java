// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.CustomWrap;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.softwrap.mapping.CachingSoftWrapDataMapper;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapApplianceManager;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.DocumentEventUtil;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

@ApiStatus.Internal
public final class SoftWrappingEnabledRecalculationManager extends SoftWrapRecalculationManager {
  /**
   * There is a possible case that particular activity performs batch fold regions operations (addition, removal etc.).
   * We don't want to process them at the same time we get notifications about that because there is a big chance that
   * we see inconsistent state (e.g., there was a problem with {@link FoldingModel#getCollapsedRegionAtOffset(int)} because that
   * method uses caching internally and cached data becomes inconsistent if, for example, the top region is removed).
   * <p/>
   * So, our strategy is to collect information about changed fold regions and process it only when batch folding processing ends.
   */
  private final List<Segment> deferredFoldRegions = new ArrayList<>();

  // visible for ExperimentalSoftWrapModelImpl#reinitRecalculationManager
  public final CachingSoftWrapDataMapper myDataMapper;
  private final SoftWrapApplianceManager myApplianceManager;
  private final SoftWrapsStorage myStorage;
  private final @NotNull DocumentEx myDocument;
  private final @NotNull EditorImpl myEditor;
  private final @NotNull SoftWrapChangeNotifier mySoftWrapChangeNotifier;
  private final @NotNull BooleanSupplier myBulkDocumentUpdateInProgress;

  /**
   * Soft wraps must be kept up to date on document changes.
   * During document update processing, editor state used by soft wraps can be transiently inconsistent
   * (e.g., fold model advances fold region offsets when end-user types before it;
   * hence, fold regions data is inconsistent between the moment
   * when text changes are applied to the document and fold data is actually updated).
   * <p>
   * This flag indicates that soft wrap recalculations should wait until the document update is processed.
   * <p>
   * We avoid relying on {@link DocumentEx#isInEventsHandling()} which is not granular enough:
   * less prioritized {@link com.intellij.openapi.editor.event.DocumentListener} instances
   * may safely trigger soft-wrap recalculation on document events.
   */
  private boolean myDocumentUpdateInProgress;

  /**
   * Soft wraps also depend on folding-related updates: adding/removing/expanding/collapsing.
   * Folding model can report individual region changes before it reaches a consistent final state.
   * <p>
   * This flag indicates that soft wrap processing should wait until fold processing is finished.
   */
  private boolean myFoldingUpdateInProgress;

  /**
   * There is a possible case that target document is changed while its editor is inactive (e.g., user opens two editors for classes
   * {@code 'Part'} and {@code 'Whole'}; activates editor for the class {@code 'Whole'} and performs 'rename class'
   * for {@code 'Part'} from it). Soft wraps cache is not recalculated during that because corresponding editor is not shown,
   * and we lack information about visible area width. Hence, we will need to recalculate the whole soft wraps cache as soon
   * as target editor becomes visible.
   * <p/>
   * Current field serves as a flag for that {@code 'dirty document, need complete soft wraps cache recalculation'} state.
   */
  private boolean myDirty;

  private boolean myAfterLineEndInlayUpdated;
  private boolean myInlayChangedInBatchMode;

  @ApiStatus.Internal
  public SoftWrappingEnabledRecalculationManager(@NotNull EditorImpl editor,
                                                 @NotNull SoftWrapsStorage storage,
                                                 @NotNull SoftWrapPainter painter,
                                                 @NotNull SoftWrapNotifier softWrapNotifier,
                                                 @NotNull BooleanSupplier bulkDocumentUpdateInProgress) {
    myEditor = editor;
    myDocument = editor.getElfDocument();
    myStorage = storage;
    mySoftWrapChangeNotifier = softWrapNotifier;
    myDataMapper = new CachingSoftWrapDataMapper(editor, storage, softWrapNotifier);
    myApplianceManager = new SoftWrapApplianceManager(storage, editor, painter, myDataMapper, softWrapNotifier);
    myBulkDocumentUpdateInProgress = bulkDocumentUpdateInProgress;
  }

  /**
   * Encapsulates preparations for performing document dimension mapping (e.g., visual to logical position) and answers
   * if soft wraps-aware processing should be used (e.g., there is no need to consider soft wraps if user configured them
   * not to be used).
   */
  @Override
  public void prepareToMapping() {
    if (myDocumentUpdateInProgress || myFoldingUpdateInProgress || myBulkDocumentUpdateInProgress.getAsBoolean() || myEditor.isPurePaintingMode()) {
      return;
    }

    if (myDirty) {
      myStorage.removeAll();
      mySoftWrapChangeNotifier.notifySoftWrapsChanged();
      myApplianceManager.reset();
      deferredFoldRegions.clear();
      myDirty = false;
    }

    myApplianceManager.recalculateIfNecessary();
  }

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent event) {
    if (myBulkDocumentUpdateInProgress.getAsBoolean()) {
      return;
    }
    myAfterLineEndInlayUpdated = false;
    myDocumentUpdateInProgress = true;
    if (myEditor.isPurePaintingMode()) {
      myDirty = true;
      return;
    }
    myApplianceManager.beforeDocumentChange(event);
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent event) {
    if (myBulkDocumentUpdateInProgress.getAsBoolean()) {
      return;
    }
    myDocumentUpdateInProgress = false;
    if (myEditor.getCustomWrapModel().hasWraps() && DocumentEventUtil.isMoveInsertion(event)) {
      int dstOffset = event.getOffset();
      int srcOffset = event.getMoveOffset();
      if (dstOffset < srcOffset) {
        // offset before the insertion of the moved fragment, which corresponds to current state in storage
        int srcOffsetBefore = srcOffset - event.getNewLength();
        // they will be reinserted by recalculation for the following move deletion
        myStorage.removeCustomWrapsInRange(srcOffsetBefore, srcOffset);
      }
    }
    myApplianceManager.documentChanged(event, myAfterLineEndInlayUpdated);
    if (DocumentEventUtil.isMoveInsertion(event)) {
      int dstOffset = event.getOffset();
      int srcOffset = event.getMoveOffset();
      int textLength = event.getDocument().getTextLength();
      // adding +1, as inlays at the end of the moved range stick to the following text (and impact its layout)
      myApplianceManager.recalculate(Arrays.asList(new TextRange(srcOffset, Math.min(textLength, srcOffset + event.getNewLength() + 1)),
                                                   new TextRange(dstOffset, Math.min(textLength, dstOffset + event.getNewLength() + 1))));
    }
  }

  @Override
  public void onBulkDocumentUpdateStarted() {
  }

  @Override
  public void onBulkDocumentUpdateFinished() {
    recalculate();
  }

  @Override
  public void onFoldRegionStateChange(@NotNull FoldRegion region) {
    myFoldingUpdateInProgress = true;
    if (myEditor.isPurePaintingMode() || !region.isValid()) {
      myDirty = true;
      return;
    }

    // We delay processing of changed fold regions till the invocation of onFoldProcessingEnd(), as
    // FoldingModel can return inconsistent data before that moment.
    deferredFoldRegions.add(region.getTextRange()); // copy because region can become invalid later
  }

  @Override
  public void onFoldProcessingEnd() {
    myFoldingUpdateInProgress = false;
    if (myEditor.isPurePaintingMode()) {
      return;
    }
    try {
      if (!myDirty) { // no need to recalculate specific areas if the whole document will be reprocessed
        myApplianceManager.recalculate(deferredFoldRegions);
      }
    }
    finally {
      deferredFoldRegions.clear();
    }
  }

  @Override
  public void onUpdated(@NotNull Inlay<?> inlay, int changeFlags) {
    if (myBulkDocumentUpdateInProgress.getAsBoolean() ||
        inlay.getPlacement() != Inlay.Placement.INLINE && inlay.getPlacement() != Inlay.Placement.AFTER_LINE_END ||
        (changeFlags & InlayModel.ChangeFlags.WIDTH_CHANGED) == 0) {
      return;
    }
    if (myEditor.getInlayModel().isInBatchMode()) {
      myInlayChangedInBatchMode = true;
      return;
    }
    if (myEditor.isPurePaintingMode()) {
      myDirty = true;
      return;
    }
    if (!myDirty) {
      if (myDocumentUpdateInProgress) {
        if (inlay.getPlacement() == Inlay.Placement.AFTER_LINE_END) {
          myAfterLineEndInlayUpdated = true;
        }
        return;
      }
      int offset = inlay.getOffset();
      if (inlay.getPlacement() == Inlay.Placement.AFTER_LINE_END) {
        offset = DocumentUtil.getLineEndOffset(offset, myDocument);
      }
      myApplianceManager.recalculate(Collections.singletonList(new TextRange(offset, offset)));
    }
  }

  @Override
  public void onBatchModeFinish(@NotNull Editor editor) {
    if (myBulkDocumentUpdateInProgress.getAsBoolean()) return;
    if (myInlayChangedInBatchMode) {
      myInlayChangedInBatchMode = false;
      recalculate();
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (EditorEx.PROP_FONT_SIZE.equals(evt.getPropertyName())) {
      myDirty = true;
    }
  }

  @Override
  public void reset() {
    myApplianceManager.reset();
    deferredFoldRegions.clear();
  }

  @Override
  public boolean isResetNeeded(boolean tabWidthChanged, boolean fontChanged) {
    return tabWidthChanged || fontChanged;
  }

  public void setSoftWrapPainter(@NotNull SoftWrapPainter painter) {
    myApplianceManager.setSoftWrapPainter(painter);
  }

  @Override
  public boolean isDirty() {
    return myDirty;
  }

  @Override
  public void release() {
    deferredFoldRegions.clear();
  }

  @Override
  public void recalculate() {
    if (myEditor.isPurePaintingMode()) {
      myDirty = true;
      return;
    }
    myDirty = false;
    myApplianceManager.reset();
    myStorage.removeAll();
    mySoftWrapChangeNotifier.notifySoftWrapsChanged();
    deferredFoldRegions.clear();
    myApplianceManager.recalculateIfNecessary();
  }

  public SoftWrapApplianceManager getApplianceManager() {
    return myApplianceManager;
  }

  public void recalculateAll() {
    myDirty = false;
    myApplianceManager.recalculateAll();
  }

  @Override
  public @NotNull String dumpState() {
    return String.format(
      """
        document update in progress: %b, folding update in progress: %b, dirty: %b, deferred regions: %s
        appliance manager state: %s
        soft wraps mapping info: %s""",
      myDocumentUpdateInProgress, myFoldingUpdateInProgress, myDirty, deferredFoldRegions,
      myApplianceManager.dumpState(),
      myDataMapper.dumpState());
  }

  @Override
  public @NotNull String dumpName() {
    return "default";
  }

  @Override
  public void customWrapAdded(@NotNull CustomWrap wrap) {
    myApplianceManager.recalculate(Collections.singletonList(new TextRange(wrap.getOffset(), wrap.getOffset())));
  }

  @Override
  public void customWrapRemoved(@NotNull CustomWrap wrap) {
    if (myDocumentUpdateInProgress) {
      // wrap was removed due to a document change, recalculation will be handled in #documentChanged
      return;
    }
    myApplianceManager.recalculate(Collections.singletonList(new TextRange(wrap.getOffset(), wrap.getOffset())));
  }
}
