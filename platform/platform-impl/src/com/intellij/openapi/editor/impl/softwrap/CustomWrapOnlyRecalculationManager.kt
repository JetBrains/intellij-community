// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap

import com.intellij.openapi.diagnostic.AttachmentFactory
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.CustomWrap
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.customwrap.CustomWrapImpl
import com.intellij.openapi.editor.impl.softwrap.CustomWrapToSoftWrapAdapter.Type
import com.intellij.openapi.editor.impl.softwrap.mapping.IncrementalCacheUpdateEvent
import com.intellij.openapi.util.Segment
import com.intellij.util.DocumentEventUtil
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
  private var isFoldingUpdateInProgress: Boolean = false
  private val deferredFoldRegions: MutableList<Segment> = ArrayList()

  private var customWrapsMerged: Boolean = false
  private var customWrapsMoved: Boolean = false
  private var customWrapRemovedByUpdate: Boolean = false

  override var isDirty: Boolean = false
    private set

  override fun prepareToMapping() {
    if (!isDirty || isDocumentUpdateInProgress || isFoldingUpdateInProgress || isBulkDocumentUpdateInProgress.getAsBoolean()) {
      return
    }
    isDirty = false
    storage.removeAll()
    deferredFoldRegions.clear()
    recalculateCustomWraps(IncrementalCacheUpdateEvent.forWholeDocument(editor.elfDocument))
  }

  override fun propertyChange(evt: PropertyChangeEvent?) {}

  override fun dumpState(): @NonNls String = """
    document update in progress: $isDocumentUpdateInProgress, folding update in progress: $isFoldingUpdateInProgress, dirty: $isDirty, 
    deferred fold regions: $deferredFoldRegions,
    customWrapsMerged: $customWrapsMerged, customWrapsMoved: $customWrapsMoved, customWrapRemovedByUpdate: $customWrapRemovedByUpdate
    """.trimIndent()

  override fun reset() {
    isDirty = true
    deferredFoldRegions.clear()
  }

  override fun isResetNeeded(tabWidthChanged: Boolean, fontChanged: Boolean): Boolean {
    return false
  }

  override fun release() {
    deferredFoldRegions.clear()
  }

  override fun recalculate() {
    storage.removeAll()
    softWrapNotifier.notifySoftWrapsChanged()
    deferredFoldRegions.clear()
    recalculateCustomWraps(IncrementalCacheUpdateEvent.forWholeDocument(editor.elfDocument))
    softWrapNotifier.notifyAllDirtyRegionsReparsed()
  }

  override fun dumpName() = "custom wraps only"

  override fun beforeDocumentChange(event: DocumentEvent) {
    if (isBulkDocumentUpdateInProgress.getAsBoolean()) {
      return
    }
    isDocumentUpdateInProgress = true
    if (DocumentEventUtil.isMoveInsertion(event) && checkHasWraps()) {
      // Custom wraps in the moved-from range will become out-of-order inside storage after the document change is applied.
      // They must be moved now.
      val srcStartOffset = DocumentEventUtil.getMoveOffsetBeforeInsertion(event)
      val srcEndOffset = srcStartOffset + event.newLength
      // points to the first custom-wrap with offset >= affectedStartOffset
      val srcStartIndex = storage.getSoftWrapIndex(srcStartOffset).let { if (it >= 0) it else -it - 1 }
      val srcEndIndex = storage.getSoftWrapIndex(srcEndOffset).let { if (it >= 0) it else -it - 1 }
      if (srcStartIndex == srcEndIndex) {
        // no custom wraps in the moved-from range
        return
      }
      // Maybe we needn't move them in storage, but in relation to the document, they *will* be moved by the event.
      // We should let listeners know about the change.
      customWrapsMoved = true
      val dstOffset = event.offset
      val dstIndex = storage.getSoftWrapIndex(dstOffset).let { if (it >= 0) it else -it - 1 }
      if (
        dstIndex == srcStartIndex || // move to the left
        dstIndex == srcEndIndex // move to the right
      ) {
        // no custom wraps between source and destination
        return
      }
      storage.moveSegment(srcStartIndex, srcEndIndex, dstIndex)
    }
  }

  override fun documentChanged(event: DocumentEvent) {
    if (isBulkDocumentUpdateInProgress.getAsBoolean()) {
      return
    }
    isDocumentUpdateInProgress = false
    if (customWrapsMerged || customWrapRemovedByUpdate) {
      recalculateCustomWraps(createEventForDocumentChange(event))
    }
    if (customWrapsMoved || customWrapsMerged || customWrapRemovedByUpdate) {
      customWrapsMoved = false
      customWrapsMerged = false
      customWrapRemovedByUpdate = false
      softWrapNotifier.notifyAllDirtyRegionsReparsed()
    }
  }

  override fun onBulkDocumentUpdateStarted() {}

  override fun onBulkDocumentUpdateFinished() {
    recalculate()
  }

  override fun onFoldRegionStateChange(region: FoldRegion) {
    isFoldingUpdateInProgress = true
    if (!checkHasWraps()) {
      return
    }
    deferredFoldRegions.add(region)
  }

  override fun onFoldProcessingEnd() {
    isFoldingUpdateInProgress = false
    if (!checkHasWraps()) {
      return
    }
    // todo improve
    deferredFoldRegions.forEach { recalculateCustomWraps(createEventForVisualChange(it.startOffset, it.endOffset)) }
    deferredFoldRegions.clear()
    softWrapNotifier.notifyAllDirtyRegionsReparsed()
  }

  override fun customWrapAdded(wrap: CustomWrap) {
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
      customWrapRemovedByUpdate = true
      return
    }
    recalculateCustomWraps(createEventForVisualChange(wrap.offset, wrap.offset))
    softWrapNotifier.notifyAllDirtyRegionsReparsed()
  }

  private fun modelHasWraps(): Boolean = editor.customWrapModel.hasWraps()

  private fun storageHasWraps(): Boolean = !storage.isEmpty

  private fun checkHasWraps(): Boolean {
    checkStoragesAreSynced()
    return storageHasWraps()
  }

  private fun checkStoragesAreSynced() {
    if (modelHasWraps() != storageHasWraps()) {
      LOG.error("CustomWrapModel and storage are not synced", Throwable(), AttachmentFactory.createContext(editor.dumpState()))
    }
  }

  private fun recalculateCustomWraps(event: IncrementalCacheUpdateEvent) {
    val startOffset = event.startOffset
    val endOffset = event.mandatoryEndOffset

    softWrapNotifier.notifyRegionReparseStart(event)

    val actual: List<CustomWrap> = editor.customWrapModel
      .getWrapsInRange(startOffset, endOffset)
    val replacement = ArrayList<CustomWrapToSoftWrapAdapter>()
    actual.forEach {
      replacement.addLastIfNotFoldedOrDuplicated(it, editor)
    }
    val startIndex = storage.getSoftWrapIndex(startOffset).let { if (it >= 0) it else -it - 1 }
    val endIndex = run {
      var index = storage.getSoftWrapIndex(endOffset).let { if (it >= 0) it + 1 else -it - 1 }
      if (index < 0) {
        (-index) - 1
      }
      else {
        // endIndex needs to point past wraps at endOffset
        val wraps = storage.softWraps
        while (index < wraps.size && wraps[index].start == endOffset) {
          index++
        }
        index
      }
    }
    storage.replaceSegment(startIndex, endIndex, replacement)

    event.actualEndOffset = endOffset
    softWrapNotifier.notifyRegionReparseEnd(event)
  }

  override fun customWrapsMerged() {
    assert(isDocumentUpdateInProgress)
    customWrapsMerged = true
  }
}

private val LOG = logger<CustomWrapOnlyRecalculationManager>()

private fun MutableList<CustomWrapToSoftWrapAdapter>.addLastIfNotFoldedOrDuplicated(customWrap: CustomWrap, editor: EditorImpl) {
  if (editor.foldingModel.isOffsetCollapsed(customWrap.offset)) {
    return
  }
  val lastAdded: SoftWrapEx? = this.lastOrNull()
  if (lastAdded != null && lastAdded.getStart() == customWrap.offset) {
    return
  }
  this.addLast(CustomWrapToSoftWrapAdapter(customWrap, Type.PASS_THROUGH, editor))
}

private fun createEventForDocumentChange(event: DocumentEvent): IncrementalCacheUpdateEvent {
  return createIncrementalUpdateEvent(event.offset, event.offset + event.oldLength, event.offset + event.newLength)
}

private fun createEventForVisualChange(startOffset: Int, endOffset: Int): IncrementalCacheUpdateEvent =
  createIncrementalUpdateEvent(startOffset, endOffset, endOffset)

private fun createIncrementalUpdateEvent(startOffset: Int, oldEndOffset: Int, newEndOffset: Int): IncrementalCacheUpdateEvent =
  IncrementalCacheUpdateEvent(startOffset, newEndOffset, newEndOffset - oldEndOffset)
