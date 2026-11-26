// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.impl.marker.DefaultMarkerPolicy
import com.intellij.openapi.editor.impl.marker.MarkerPolicy
import com.intellij.openapi.editor.impl.marker.MarkerSpec
import com.intellij.openapi.editor.impl.marker.MarkerTransformResult
import com.intellij.openapi.editor.impl.marker.PMarkerRelocationRoot
import com.intellij.openapi.editor.impl.marker.PMarkerResolution
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerReference
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerRootStore
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.util.TextRangeScalarUtil

/**
 * Stores caret position and selection markers for one editor.
 * [runInBatch] defers eligible position moves while each document edit still reports affected markers.
 */
internal class SnapshotCaretMarkerStorage(
  val document: DocumentImpl,
) {
  /** Enables deferred position moves only while the batch callback executes. */
  private var batching: Boolean = false
  private var rootBeforeChange: PMarkerRoot? = null
  private var affectedHandles: List<SnapshotMarkerReference<SnapshotCaretMarker>> = emptyList()
  val rootStore: SnapshotMarkerRootStore<SnapshotCaretMarker> = SnapshotMarkerRootStore(
    document, SnapshotCaretMarker::class.java,
    onDocumentChanged = ::documentChanged,
    onMarkersAffected = { affectedHandles = it },
  )

  /** Captures the root that supplies the caret offsets before the edit. */
  fun beforeDocumentChange() {
    rootBeforeChange = rootStore.root(currentSnapshot())
  }

  /** Notifies live affected carets that overlap the old edit range. Passes the start offset from the captured root. */
  private fun documentChanged(event: DocumentEvent) {
    val beforeRoot = rootBeforeChange
    val affected = affectedHandles
    rootBeforeChange = null
    affectedHandles = emptyList()
    if (beforeRoot == null) return
    for (index in affected.indices) {
      val handle = affected[index]
      val marker = handle.get() ?: continue
      if (marker.isDisposed()) continue
      val oldRange = beforeRoot.resolve(handle)
      val oldStart = PMarkerResolution.startOffset(oldRange)
      if (oldStart <= event.offset + event.oldLength && PMarkerResolution.endOffset(oldRange) >= event.offset) {
        marker.documentChanged(event, oldStart)
      }
    }
  }

  fun dispose() {
    rootStore.dispose(document.snapshotMarkerStores)
    rootBeforeChange = null
    affectedHandles = emptyList()
  }

  /**
   * Runs [runnable] with deferred position moves. Calls must not nest.
   * Resets the batching flag before materializing pending moves, including when [runnable] throws.
   */
  fun runInBatch(runnable: Runnable) {
    check(!batching)
    PMarkerRelocationRoot.runInBatch(rootStore) {
      batching = true
      try {
        runnable.run()
      }
      finally {
        batching = false
      }
    }
  }

  /** Moves a live position marker. Defers eligible moves within [runInBatch] and applies other moves immediately. */
  fun movePositionMarker(marker: SnapshotCaretMarker, offset: Int) {
    val entry = PMarkerRoot.MarkerEntry(
      marker.markerHandle, offset, offset, POSITION_SPEC, marker.flavorFlags, policy = CaretPositionMarkerPolicy,
    )
    rootStore.updateCurrentRoot { root ->
      if (marker.isDisposed()) root else PMarkerRelocationRoot.move(root, entry, defer = batching)
    }
  }

  fun add(marker: SnapshotCaretMarker, startOffset: Int, endOffset: Int, specMask: Byte, policy: MarkerPolicy) {
    rootStore.updateRoot(currentSnapshot()) {
      it.insert(marker.markerHandle, startOffset, endOffset, specMask, marker.flavorFlags, policy = policy)
    }
  }

  fun currentSnapshot(): DocumentSnapshot = document.core.snapshot()

  companion object {
    @JvmField
    val POSITION_SPEC: Byte = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)

    @JvmField
    val SELECTION_SPEC: Byte = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
  }
}

internal abstract class SnapshotCaretMarker(
  storage: SnapshotCaretMarkerStorage,
  startOffset: Int,
  endOffset: Int,
  specMask: Byte,
  policy: MarkerPolicy,
) : SnapshotRangeMarkerImpl(
  storage.document, storage.rootStore, specMask,
  TextRangeScalarUtil.toScalarRange(startOffset, endOffset),
) {
  init {
    storage.add(this, startOffset, endOffset, specMask, policy)
  }

  /** Handles a document change that affects this marker. [oldOffset] is the marker's start before the change. */
  abstract fun documentChanged(event: DocumentEvent, oldOffset: Int): Unit
}

internal object CaretPositionMarkerPolicy : MarkerPolicy {
  override fun transform(
    entry: PMarkerRoot.MarkerEntry,
    patch: DocumentPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
  ): MarkerTransformResult {
    val transformed = DefaultMarkerPolicy.transform(entry, patch, beforeText, afterText)
    val offset = if (transformed.isValid) {
      transformed.startOffset
    }
    else {
      minOf(entry.nodeStart, patch.startOffset() + patch.newFragment().length)
    }
    return alignPoint(offset, afterText)
  }

  override fun afterRetarget(entry: PMarkerRoot.MarkerEntry, text: DocumentText): MarkerTransformResult {
    return alignPoint(entry.nodeStart, text)
  }

  private fun alignPoint(startOffset: Int, text: DocumentText): MarkerTransformResult {
    val offset = alignToCodePointBoundary(startOffset, text)
    return MarkerTransformResult(offset, offset)
  }
}

internal object CaretSelectionMarkerPolicy : MarkerPolicy {
  override fun transform(
    entry: PMarkerRoot.MarkerEntry,
    patch: DocumentPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
  ): MarkerTransformResult {
    val transformed = DefaultMarkerPolicy.transform(entry, patch, beforeText, afterText)
    return if (!transformed.isValid) {
      transformed
    }
    else {
      alignRange(transformed, afterText)
    }
  }

  override fun afterRetarget(entry: PMarkerRoot.MarkerEntry, text: DocumentText): MarkerTransformResult {
    return alignRange(MarkerTransformResult(entry), text)
  }

  private fun alignRange(range: MarkerTransformResult, text: DocumentText): MarkerTransformResult {
    val startOffset = alignToCodePointBoundary(range.startOffset, text)
    val endOffset = alignToCodePointBoundary(range.endOffset, text)
    return MarkerTransformResult(startOffset, endOffset)
  }
}

private fun alignToCodePointBoundary(offset: Int, text: DocumentText): Int {
  if (offset <= 0 || offset >= text.length()) return offset
  val chars = text.luckyChars()
  return if (Character.isHighSurrogate(chars[offset - 1]) && Character.isLowSurrogate(chars[offset])) offset - 1 else offset
}
