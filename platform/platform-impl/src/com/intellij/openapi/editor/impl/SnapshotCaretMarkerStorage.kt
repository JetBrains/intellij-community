// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.impl.marker.DefaultMarkerPolicy
import com.intellij.openapi.editor.impl.marker.MarkerPolicy
import com.intellij.openapi.editor.impl.marker.MarkerSpec
import com.intellij.openapi.editor.impl.marker.MarkerTransformResult
import com.intellij.openapi.editor.impl.marker.PMarkerResolution
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerRootStore
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.util.TextRangeScalarUtil
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongList
import it.unimi.dsi.fastutil.longs.LongLists

/** Stores caret position and selection markers for one editor. */
internal class SnapshotCaretMarkerStorage(
  val document: DocumentImpl,
) {
  private val markers: Long2ObjectOpenHashMap<SnapshotCaretMarker> = Long2ObjectOpenHashMap()
  private var rootBeforeChange: PMarkerRoot? = null
  private var affectedMarkerIds: LongList = LongLists.EMPTY_LIST
  val rootStore: SnapshotMarkerRootStore = SnapshotMarkerRootStore(
    document,
    onDocumentChanged = ::documentChanged,
    onMarkersAffected = { affectedMarkerIds = it },
  )

  fun beforeDocumentChange() {
    rootBeforeChange = rootStore.root(currentSnapshot())
  }

  private fun documentChanged(event: DocumentEvent) {
    val beforeRoot = rootBeforeChange
    val affected = affectedMarkerIds
    rootBeforeChange = null
    affectedMarkerIds = LongLists.EMPTY_LIST
    if (beforeRoot == null) return
    for (index in affected.indices) {
      val markerId = affected.getLong(index)
      val marker = markers.get(markerId) ?: continue
      val oldRange = beforeRoot.resolve(markerId)
      val oldStart = PMarkerResolution.startOffset(oldRange)
      if (oldStart <= event.offset + event.oldLength && PMarkerResolution.endOffset(oldRange) >= event.offset) {
        marker.documentChanged(event, oldStart)
      }
    }
  }

  fun dispose() {
    rootStore.dispose(document.snapshotMarkerStores)
    markers.clear()
    rootBeforeChange = null
    affectedMarkerIds = LongLists.EMPTY_LIST
  }

  fun nextMarkerId(): Long = SnapshotMarkerEngineImpl.nextMarkerId()

  fun positionSpec(): Byte = POSITION_SPEC

  fun selectionSpec(): Byte = SELECTION_SPEC

  fun positionPolicy(): MarkerPolicy = CaretPositionMarkerPolicy

  fun selectionPolicy(): MarkerPolicy = CaretSelectionMarkerPolicy

  fun add(marker: SnapshotCaretMarker, startOffset: Int, endOffset: Int, spec: Byte) {
    markers.put(marker.id, marker)
    rootStore.updateRoot(currentSnapshot()) {
      it.insert(marker.id, startOffset, endOffset, spec, marker.flavorFlags, policy = marker.policy)
    }
  }

  fun remove(marker: SnapshotCaretMarker) {
    markers.remove(marker.id)
  }

  fun relocate(marker: SnapshotRangeMarkerImpl, offset: Int, spec: Byte) {
    rootStore.updateRoot(currentSnapshot()) {
      it.remove(marker.id).insert(marker.id, offset, offset, spec, marker.flavorFlags, policy = marker.policy)
    }
  }

  fun currentSnapshot(): DocumentSnapshot = document.core.snapshot()

  companion object {
    private val POSITION_SPEC: Byte = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    private val SELECTION_SPEC: Byte = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
  }
}

internal abstract class SnapshotCaretMarker(
  protected val storage: SnapshotCaretMarkerStorage,
  startOffset: Int,
  endOffset: Int,
  spec: Byte,
  policy: MarkerPolicy,
) : SnapshotRangeMarkerImpl(
  storage.document, storage.rootStore, storage.nextMarkerId(), spec,
  TextRangeScalarUtil.toScalarRange(startOffset, endOffset), policy,
) {
  init {
    storage.add(this, startOffset, endOffset, spec)
  }

  abstract fun documentChanged(event: DocumentEvent, oldOffset: Int)

  override fun afterDispose() {
    storage.remove(this)
  }
}

private object CaretPositionMarkerPolicy : MarkerPolicy {
  override fun transform(
      entry: PMarkerRoot.MarkerEntry,
      patch: DocumentPatch,
      beforeText: DocumentText,
      afterText: DocumentText,
  ): MarkerTransformResult {
    val transformed = DefaultMarkerPolicy.transform(entry, patch, beforeText, afterText)
    val updatedEntry = if (transformed.errorReason == null) {
      transformed.entry
    }
    else {
      val offset = minOf(entry.nodeStart, patch.startOffset() + patch.newFragment().length)
      entry.copy(nodeStart = offset, nodeEnd = offset)
    }
    return MarkerTransformResult(alignPoint(updatedEntry, afterText))
  }

  override fun afterRetarget(entry: PMarkerRoot.MarkerEntry, text: DocumentText): MarkerTransformResult {
    return MarkerTransformResult(alignPoint(entry, text))
  }

  private fun alignPoint(entry: PMarkerRoot.MarkerEntry, text: DocumentText): PMarkerRoot.MarkerEntry {
    val offset = alignToCodePointBoundary(entry.nodeStart, text)
    return if (offset == entry.nodeStart) entry else entry.copy(nodeStart = offset, nodeEnd = offset)
  }
}

private object CaretSelectionMarkerPolicy : MarkerPolicy {
  override fun transform(
      entry: PMarkerRoot.MarkerEntry,
      patch: DocumentPatch,
      beforeText: DocumentText,
      afterText: DocumentText,
  ): MarkerTransformResult {
    val transformed = DefaultMarkerPolicy.transform(entry, patch, beforeText, afterText)
    return if (transformed.errorReason != null) {
      transformed
    }
    else {
      MarkerTransformResult(alignRange(transformed.entry, afterText))
    }
  }

  override fun afterRetarget(entry: PMarkerRoot.MarkerEntry, text: DocumentText): MarkerTransformResult {
    return MarkerTransformResult(alignRange(entry, text))
  }

  private fun alignRange(entry: PMarkerRoot.MarkerEntry, text: DocumentText): PMarkerRoot.MarkerEntry {
    val startOffset = alignToCodePointBoundary(entry.nodeStart, text)
    val endOffset = alignToCodePointBoundary(entry.nodeEnd, text)
    return if (startOffset == entry.nodeStart && endOffset == entry.nodeEnd) {
      entry
    }
    else {
      entry.copy(nodeStart = startOffset, nodeEnd = endOffset)
    }
  }
}

private fun alignToCodePointBoundary(offset: Int, text: DocumentText): Int {
  if (offset <= 0 || offset >= text.length()) return offset
  val chars = text.cachedChars()
  return if (Character.isHighSurrogate(chars[offset - 1]) && Character.isLowSurrogate(chars[offset])) offset - 1 else offset
}
