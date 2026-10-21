// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.impl.marker.MarkerSpec
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerReference
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerRootStore
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.util.Processor
import com.intellij.util.containers.ConcurrentLongObjectMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

/** Stores focus regions for one editor without adding them to the document marker root. */
internal class SnapshotFocusRegionStorage(val document: DocumentImpl) {
  private val regionQueue: ReferenceQueue<SnapshotFocusRegion> = ReferenceQueue<SnapshotFocusRegion>()
  private val regionsById: ConcurrentLongObjectMap<RegionReference> = ConcurrentCollectionFactory.createConcurrentLongObjectMap()
  val rootStore: SnapshotMarkerRootStore = SnapshotMarkerRootStore(document)

  fun dispose() {
    rootStore.dispose(document.snapshotMarkerStores)
    regionsById.clear()
  }

  fun create(startOffset: Int, endOffset: Int): SnapshotFocusRegion {
    processQueue()
    val snapshot = currentSnapshot()
    require(startOffset >= 0) { "startOffset must be non-negative" }
    require(endOffset >= startOffset) { "endOffset must not precede startOffset" }
    require(endOffset <= snapshot.text().length()) { "Focus region exceeds the snapshot length" }

    val markerId = SnapshotMarkerEngineImpl.nextMarkerId()
    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false, isStickingToRight = true)
    val region = SnapshotFocusRegion(this, markerId, spec, TextRangeScalarUtil.toScalarRange(startOffset, endOffset))
    val regionReference = RegionReference(region, regionQueue)
    check(regionsById.putIfAbsent(markerId, regionReference) == null) { "Focus region $markerId is already registered" }
    rootStore.updateRoot(snapshot) {
      it.insert(markerId, startOffset, endOffset, spec, region.flavorFlags, regionReference, policy = region.policy)
    }
    return region
  }

  fun remove(region: SnapshotFocusRegion): Boolean {
    val markerId = region.id
    val reference = regionsById.get(markerId)
    if (reference?.get() !== region || !region.isValid) return false
    region.dispose()
    return true
  }

  fun processContaining(offset: Int, processor: Processor<in RangeMarkerEx>): Boolean {
    return processOverlapping(offset, offset) { region ->
      if (region.startOffset <= offset && offset <= region.endOffset) processor.process(region) else true
    }
  }

  fun processOverlapping(startOffset: Int, endOffset: Int, processor: Processor<in RangeMarkerEx>): Boolean {
    processQueue()
    val snapshot = currentSnapshot()
    val root = rootStore.root(snapshot) ?: return true
    var collectedMarkerIds: LongArrayList? = null
    try {
      return root.processRangeMarkersOverlappingWith(startOffset, endOffset, 0) { entry ->
        val region = entry.markerReference?.get() as? SnapshotFocusRegion
        if (region == null) {
          val markerIds = collectedMarkerIds ?: LongArrayList().also { collectedMarkerIds = it }
          markerIds.add(entry.markerId)
          true
        }
        else {
          processor.process(region)
        }
      }
    }
    finally {
      collectedMarkerIds?.let { rootStore.purgeMany(snapshot, it) }
    }
  }

  fun currentSnapshot(): DocumentSnapshot = document.core.snapshot()

  fun afterDisposed(region: SnapshotFocusRegion) {
    val markerId = region.id
    val reference = regionsById.get(markerId)
    if (reference?.get() === region) regionsById.remove(markerId, reference)
  }

  private fun processQueue() {
    var collectedMarkerIds: LongArrayList? = null
    while (true) {
      val reference = regionQueue.poll() as? RegionReference ?: break
      if (regionsById.remove(reference.markerId, reference)) {
        val markerIds = collectedMarkerIds ?: LongArrayList().also { collectedMarkerIds = it }
        markerIds.add(reference.markerId)
      }
    }
    collectedMarkerIds?.let { rootStore.purgeMany(currentSnapshot(), it) }
  }

  private class RegionReference(
    region: SnapshotFocusRegion,
    queue: ReferenceQueue<SnapshotFocusRegion>,
  ) : WeakReference<SnapshotFocusRegion>(region, queue), SnapshotMarkerReference {
    val markerId: Long = region.id

    override fun get(): SnapshotFocusRegion? = super.get()
  }
}

internal class SnapshotFocusRegion(
  private val storage: SnapshotFocusRegionStorage,
  markerId: Long,
  spec: Byte,
  initialRange: Long,
) : SnapshotRangeMarkerImpl(storage.document, storage.rootStore, markerId, spec, initialRange) {
  override fun afterDispose() {
    storage.afterDisposed(this)
  }
}
