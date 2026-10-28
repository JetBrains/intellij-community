// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.impl.marker.MarkerSpec
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerRootStore
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.util.Processor

/** Stores focus regions for one editor without adding them to the document marker root. */
internal class SnapshotFocusRegionStorage(val document: DocumentImpl) {
  val rootStore: SnapshotMarkerRootStore<*> = SnapshotMarkerRootStore.create(document)

  fun dispose() {
    rootStore.dispose(document.snapshotMarkerStores)
  }

  fun create(startOffset: Int, endOffset: Int): SnapshotFocusRegion {
    SnapshotMarkerEngineImpl.processQueue()
    val snapshot = currentSnapshot()
    require(startOffset >= 0) { "startOffset must be non-negative" }
    require(endOffset >= startOffset) { "endOffset must not precede startOffset" }
    require(endOffset <= snapshot.text().length()) { "Focus region exceeds the snapshot length" }

    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false, isStickingToRight = true)
    val region = SnapshotFocusRegion(this, spec, TextRangeScalarUtil.toScalarRange(startOffset, endOffset))
    rootStore.updateRoot(snapshot) {
      it.insert(region.markerHandle, startOffset, endOffset, spec, region.flavorFlags)
    }
    return region
  }

  fun remove(region: SnapshotFocusRegion): Boolean {
    if (region.storage !== this || !region.isValid) return false
    region.dispose()
    return true
  }

  fun processContaining(offset: Int, processor: Processor<in RangeMarkerEx>): Boolean {
    return processOverlapping(offset, offset) { region ->
      if (region.startOffset <= offset && offset <= region.endOffset) processor.process(region) else true
    }
  }

  fun processOverlapping(startOffset: Int, endOffset: Int, processor: Processor<in RangeMarkerEx>): Boolean {
    return SnapshotMarkerEngineImpl.processRangeMarkersOverlappingWith(rootStore, currentSnapshot(), startOffset, endOffset, 0, processor)
  }

  fun currentSnapshot(): DocumentSnapshot = document.core.snapshot()
}

internal class SnapshotFocusRegion(
  internal val storage: SnapshotFocusRegionStorage,
  spec: Byte,
  initialRange: Long,
) : SnapshotRangeMarkerImpl(storage.document, storage.rootStore, spec, initialRange)
