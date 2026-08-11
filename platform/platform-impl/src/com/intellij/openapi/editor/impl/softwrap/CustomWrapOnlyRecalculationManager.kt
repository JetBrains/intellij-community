// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax")

package com.intellij.openapi.editor.impl.softwrap

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.CustomWrap
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.softwrap.SoftWrapHelper.coerceToValidOffset
import com.intellij.openapi.editor.impl.softwrap.mapping.IncrementalCacheUpdateEvent
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.NonNls
import java.beans.PropertyChangeEvent
import java.util.function.BooleanSupplier

internal class CustomWrapOnlyRecalculationManager(
  private val editor: EditorImpl,
  private val storage: SoftWrapsStorage,
  private val softWrapNotifier: SoftWrapNotifier,
  private val isBulkDocumentUpdateInProgress: BooleanSupplier,
) : SoftWrapRecalculationManager() {
  private var isDocumentUpdateInProgress: Boolean = false
  private var documentUpdateStartOffset: Int = -1
  private var documentUpdateEndOffset: Int = -1

  private var isFoldingUpdateInProgress: Boolean = false
  private val deferredFoldRegions: MutableList<Segment> = ArrayList()

  private var isCustomWrapUpdateInProgress: Boolean = false
  // todo: use IntList
  private val deferredCustomWraps: MutableList<Segment> = ArrayList()

  override var isDirty: Boolean = false
    private set

  override fun prepareToMapping() {
    if (!isDirty ||
        isDocumentUpdateInProgress ||
        isFoldingUpdateInProgress ||
        isCustomWrapUpdateInProgress ||
        isBulkDocumentUpdateInProgress.getAsBoolean()) {
      return
    }
    isDirty = false
    storage.removeAll()
    deferredFoldRegions.clear()
    deferredCustomWraps.clear()
    recalculateCustomWraps(IncrementalCacheUpdateEvent.forWholeDocument(editor.elfDocument))
  }

  override fun propertyChange(evt: PropertyChangeEvent?) {}

  override fun dumpState(): @NonNls String = """
    document update in progress: $isDocumentUpdateInProgress, folding update in progress: $isFoldingUpdateInProgress, dirty: $isDirty, 
    deferred fold regions: $deferredFoldRegions,
    """.trimIndent()

  override fun reset() {
    isDirty = true
    deferredFoldRegions.clear()
    deferredCustomWraps.clear()
  }

  override fun isResetNeeded(tabWidthChanged: Boolean, fontChanged: Boolean): Boolean {
    return false
  }

  override fun release() {
    deferredFoldRegions.clear()
    deferredCustomWraps.clear()
  }

  override fun recalculate(reason: String) {
    storage.removeAll()
    softWrapNotifier.notifySoftWrapsChanged()
    deferredFoldRegions.clear()
    deferredCustomWraps.clear()
    recalculateCustomWraps(IncrementalCacheUpdateEvent.forWholeDocument(editor.elfDocument))
    softWrapNotifier.notifyAllDirtyRegionsReparsed()
  }

  override fun dumpName() = "custom wraps only"

  override fun beforeDocumentChange(event: DocumentEvent) {
    if (isBulkDocumentUpdateInProgress.getAsBoolean()) {
      return
    }
    isDocumentUpdateInProgress = true
    SoftWrapHelper.removeCustomWrapsFromMoveInsertionSource(editor.customWrapModel, storage, event)
    documentUpdateStartOffset = event.offset
    documentUpdateEndOffset = event.offset + event.newLength
  }

  override fun documentChanged(event: DocumentEvent) {
    if (isBulkDocumentUpdateInProgress.getAsBoolean()) {
      return
    }
    isDocumentUpdateInProgress = false
    val cacheUpdateEvent = IncrementalCacheUpdateEvent(
      coerceToValidOffset(documentUpdateStartOffset, event.document),
      coerceToValidOffset(documentUpdateEndOffset, event.document),
      event.newLength - event.oldLength
    )
    recalculateCustomWraps(cacheUpdateEvent)
    softWrapNotifier.notifyAllDirtyRegionsReparsed()
  }

  override fun onBulkDocumentUpdateStarted() {}

  override fun onBulkDocumentUpdateFinished() {
    recalculate("bulk document update finished")
  }

  override fun beforeFoldRegionDisposed(region: FoldRegion) {
    if (isDocumentUpdateInProgress && !isBulkDocumentUpdateInProgress.getAsBoolean()) {
      documentUpdateStartOffset = minOf(documentUpdateStartOffset, region.startOffset)
      documentUpdateEndOffset = maxOf(documentUpdateEndOffset, region.endOffset)
    }
  }

  override fun onFoldRegionStateChange(region: FoldRegion) {
    isFoldingUpdateInProgress = true
    if (!modelHasWraps()) {
      return
    }
    deferredFoldRegions.add(region)
  }

  override fun onFoldProcessingEnd() {
    isFoldingUpdateInProgress = false
    if (!modelHasWraps()) {
      return
    }
    recalculateCustomWraps(deferredFoldRegions)
    deferredFoldRegions.clear()
    softWrapNotifier.notifyAllDirtyRegionsReparsed()
  }

  override fun customWrapAdded(wrap: CustomWrap) {
    if (isBulkDocumentUpdateInProgress.getAsBoolean()) {
      return
    }
    LOG.assertTrue(!isDocumentUpdateInProgress, "CustomWrap added during document update")
    if (isCustomWrapUpdateInProgress) {
      deferredCustomWraps.add(TextRange(wrap.offset, wrap.offset))
      return
    }
    recalculateCustomWraps(createEventForVisualChange(wrap.offset, wrap.offset))
    softWrapNotifier.notifyAllDirtyRegionsReparsed()
  }

  override fun customWrapRemoved(wrap: CustomWrap) {
    if (isBulkDocumentUpdateInProgress.getAsBoolean()) {
      // wrap was removed during bulk update, full recalculation to follow in #bulkUpdateFinished
      return
    }
    if (isDocumentUpdateInProgress) {
      // wrap was removed due to a document change, recalculation will be handled in #documentChanged
      documentUpdateStartOffset = minOf(documentUpdateStartOffset, wrap.offset)
      documentUpdateEndOffset = maxOf(documentUpdateEndOffset, wrap.offset)
      return
    }
    if (isCustomWrapUpdateInProgress) {
      deferredCustomWraps.add(TextRange(wrap.offset, wrap.offset))
      return
    }
    recalculateCustomWraps(createEventForVisualChange(wrap.offset, wrap.offset))
    softWrapNotifier.notifyAllDirtyRegionsReparsed()
  }

  override fun customWrapBatchMutationFinished() {
    isCustomWrapUpdateInProgress = false
    if (isBulkDocumentUpdateInProgress.getAsBoolean()) {
      return
    }
    try {
      if (!isDirty) {
        recalculateCustomWraps(deferredCustomWraps)
        if (deferredCustomWraps.isNotEmpty()) {
          softWrapNotifier.notifyAllDirtyRegionsReparsed()
        }
      }
    }
    finally {
      deferredCustomWraps.clear()
    }
  }

  override fun customWrapBatchMutationStarted() {
    isCustomWrapUpdateInProgress = true
  }

  private fun modelHasWraps(): Boolean = editor.customWrapModel.hasWraps()

  private fun recalculateCustomWraps(ranges: List<Segment>) {
    SoftWrapHelper.recalculateSegments(ranges, softWrapNotifier) { startOffset, endOffset ->
      recalculateCustomWraps(createEventForVisualChange(startOffset, endOffset))
    }
  }

  private fun recalculateCustomWraps(event: IncrementalCacheUpdateEvent) {
    val startOffset = event.startOffset
    val endOffset = event.mandatoryEndOffset

    softWrapNotifier.notifyRegionReparseStart(event)

    editor.customWrapModel
      .getWrapsInRange(startOffset, endOffset)
      .forEach {
        storage.addLastIfNotFoldedOrDuplicated(it, editor)
      }

    event.actualEndOffset = endOffset
    softWrapNotifier.notifyRegionReparseEnd(event)
  }
}

private val LOG = logger<CustomWrapOnlyRecalculationManager>()

/**
 * @param customWrap [CustomWrap.offset] must be `>=` to all custom wraps already in the list
 */
private fun SoftWrapsStorage.addLastIfNotFoldedOrDuplicated(customWrap: CustomWrap, editor: EditorImpl) {
  val offset = customWrap.offset
  val foldingModel = editor.foldingModel
  val foldRegion = foldingModel.getCollapsedRegionAtOffset(customWrap.offset)
  if (foldRegion != null && foldRegion.startOffset != offset) {
    return
  }
  val lastAdded = getLast()
  if (lastAdded != null && lastAdded.start == customWrap.offset) {
    return
  }
  this.addLast(CustomWrapToSoftWrapAdapter(customWrap, editor))
}

private fun createEventForVisualChange(startOffset: Int, endOffset: Int): IncrementalCacheUpdateEvent =
  IncrementalCacheUpdateEvent(startOffset, endOffset, 0)
