// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.impl.marker.DefaultMarkerPolicy
import com.intellij.openapi.editor.impl.marker.MarkerPolicy
import com.intellij.openapi.editor.impl.marker.MarkerSpec
import com.intellij.openapi.editor.impl.marker.MarkerTransformResult
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerRootStore
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import java.util.function.Consumer

/** Stores caret position and selection markers for one editor. */
internal class SnapshotCaretMarkerStorage(
  val document: DocumentImpl,
  documentChanged: Consumer<DocumentEvent>,
) {
  val rootStore: SnapshotMarkerRootStore = SnapshotMarkerRootStore(document, onDocumentChanged = documentChanged::accept)

  fun dispose() {
    rootStore.dispose(document.snapshotMarkerStores)
  }

  fun nextMarkerId(): Long = SnapshotMarkerEngineImpl.nextMarkerId()

  fun positionSpec(): MarkerSpec = POSITION_SPEC

  fun selectionSpec(): MarkerSpec = SELECTION_SPEC

  fun add(marker: SnapshotRangeMarkerImpl, startOffset: Int, endOffset: Int, spec: MarkerSpec) {
    rootStore.updateRoot(currentSnapshot()) {
      it.insert(marker.id, startOffset, endOffset, spec, marker.flavorFlags)
    }
  }

  fun relocate(marker: SnapshotRangeMarkerImpl, offset: Int, spec: MarkerSpec) {
    rootStore.updateRoot(currentSnapshot()) {
      it.remove(marker.id).insert(marker.id, offset, offset, spec, marker.flavorFlags)
    }
  }

  fun currentSnapshot(): DocumentSnapshot = document.core.snapshot()

  companion object {
    private val POSITION_SPEC: MarkerSpec = MarkerSpec(isGreedyToLeft = false, isGreedyToRight = false, policy = CaretPositionMarkerPolicy)
    private val SELECTION_SPEC: MarkerSpec = MarkerSpec(isGreedyToLeft = false, isGreedyToRight = false, policy = CaretSelectionMarkerPolicy)
  }
}

private object CaretPositionMarkerPolicy : MarkerPolicy {
  override fun transform(
    entry: PMarkerRoot.MarkerEntry,
    patch: DocumentTextPatch,
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
    patch: DocumentTextPatch,
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
