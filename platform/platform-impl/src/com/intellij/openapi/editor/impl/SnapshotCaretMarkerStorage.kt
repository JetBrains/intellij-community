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
import com.intellij.openapi.editor.impl.marker.PMarkerResolution
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerReference
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerRootStore
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.util.TextRangeScalarUtil

/** Stores caret position and selection markers for one editor. */
internal class SnapshotCaretMarkerStorage(
  val document: DocumentImpl,
) {
  private var rootBeforeChange: PMarkerRoot? = null
  private var affectedHandles: List<SnapshotMarkerReference<SnapshotCaretMarker?>> = emptyList()
  val rootStore: SnapshotMarkerRootStore<SnapshotCaretMarker?> = SnapshotMarkerRootStore.create(
    document, SnapshotCaretMarker::class.java,
    onDocumentChanged = ::documentChanged,
    onMarkersAffected = { affectedHandles = it },
  )

  fun beforeDocumentChange() {
    rootBeforeChange = rootStore.root(currentSnapshot())
  }

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

  fun positionSpec(): Byte = POSITION_SPEC

  fun selectionSpec(): Byte = SELECTION_SPEC

  fun positionPolicy(): MarkerPolicy = CaretPositionMarkerPolicy

  fun selectionPolicy(): MarkerPolicy = CaretSelectionMarkerPolicy

  fun add(marker: SnapshotCaretMarker, startOffset: Int, endOffset: Int, spec: Byte, policy: MarkerPolicy) {
    rootStore.updateRoot(currentSnapshot()) {
      it.insert(marker.markerHandle, startOffset, endOffset, spec, marker.flavorFlags, policy = policy)
    }
  }

  fun currentSnapshot(): DocumentSnapshot = document.core.snapshot()

  companion object {
    private val POSITION_SPEC: Byte = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    private val SELECTION_SPEC: Byte = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
  }
}

internal abstract class SnapshotCaretMarker(
  storage: SnapshotCaretMarkerStorage,
  startOffset: Int,
  endOffset: Int,
  spec: Byte,
  policy: MarkerPolicy,
) : SnapshotRangeMarkerImpl(
  storage.document, storage.rootStore, spec,
  TextRangeScalarUtil.toScalarRange(startOffset, endOffset),
) {
  init {
    storage.add(this, startOffset, endOffset, spec, policy)
  }

  abstract fun documentChanged(event: DocumentEvent, oldOffset: Int)
}

private object CaretPositionMarkerPolicy : MarkerPolicy {
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

private object CaretSelectionMarkerPolicy : MarkerPolicy {
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
