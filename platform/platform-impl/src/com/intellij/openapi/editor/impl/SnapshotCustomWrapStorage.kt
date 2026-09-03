// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.CustomWrap
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
import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.ConcurrentLongObjectMap
import com.intellij.util.containers.Java11Shim
import it.unimi.dsi.fastutil.longs.LongList
import java.util.concurrent.atomic.AtomicReference

/** Stores the snapshot custom wraps for one editor. */
internal class SnapshotCustomWrapStorage(
  private val model: CustomWrapModelImpl,
  val document: DocumentImpl,
) {
  private val wrapsById: ConcurrentLongObjectMap<SnapshotCustomWrap> = Java11Shim.createConcurrentLongObjectMap()
  private val rootStore = SnapshotMarkerRootStore(document, onMarkersInvalidated = ::processInvalidatedWraps)

  fun dispose() {
    rootStore.dispose()
    wrapsById.clear()
  }

  fun create(offset: Int, indent: Int, priority: Int): SnapshotCustomWrap {
    val snapshot = currentSnapshot()
    val markerId = SnapshotMarkerEngineImpl.nextMarkerId()
    val spec = MarkerSpec(
      isGreedyToLeft = false,
      isGreedyToRight = false,
      isStickingToRight = false,
      policy = CustomWrapMarkerPolicy,
    )
    val wrap = SnapshotCustomWrap(this, markerId, spec, TextRange(offset, offset), indent, priority)
    check(wrapsById.putIfAbsent(markerId, wrap) == null) { "Custom wrap $markerId is already registered" }
    val markerReference = SnapshotMarkerEngineImpl.createMarkerReference(wrap, retainStrong = true)
    rootStore.updateRoot(snapshot) { it.insert(markerId, offset, offset, spec, wrap.flavorFlags, markerReference) }
    return wrap
  }

  fun remove(wrap: SnapshotCustomWrap): Boolean {
    if (wrap.storage !== this || wrapsById.get(wrap.id) !== wrap || !wrap.isValid) return false
    wrap.dispose()
    return true
  }

  fun collectAll(): List<CustomWrap> {
    val snapshot = currentSnapshot()
    return collect(snapshot, 0, snapshot.text().length())
  }

  fun collectInRange(startOffset: Int, endOffset: Int): List<CustomWrap> {
    return collect(currentSnapshot(), startOffset, endOffset)
  }

  fun hasWraps(): Boolean {
    val snapshot = currentSnapshot()
    val root = rootStore.root(snapshot) ?: return false
    var found = false
    root.processRangeMarkersOverlappingWith(0, snapshot.text().length(), 0) {
      found = true
      false
    }
    return found
  }

  fun rootReference(snapshot: DocumentSnapshot): AtomicReference<PMarkerRoot> {
    return rootStore.rootReference(snapshot)
  }

  fun currentSnapshot(): DocumentSnapshot = document.core.snapshot()

  fun afterDisposed(wrap: SnapshotCustomWrap) {
    if (wrapsById.remove(wrap.id, wrap)) model.notifyRemoved(wrap)
  }

  private fun collect(snapshot: DocumentSnapshot, startOffset: Int, endOffset: Int): List<CustomWrap> {
    val root = rootStore.root(snapshot) ?: return emptyList()
    val result = ArrayList<CustomWrap>()
    root.processRangeMarkersOverlappingWith(startOffset, endOffset, 0) { entry ->
      val wrap = entry.markerReference?.get()
      if (wrap is SnapshotCustomWrap) result.add(wrap)
      true
    }
    return result
  }

  private fun processInvalidatedWraps(invalidatedMarkerIds: LongList) {
    val size = invalidatedMarkerIds.size
    for (i in 0 until size) {
      val markerId = invalidatedMarkerIds.getLong(i)
      val wrap = wrapsById.get(markerId) ?: continue
      if (wrapsById.remove(markerId, wrap)) model.notifyRemoved(wrap)
    }
  }
}

internal class SnapshotCustomWrap(
  internal val storage: SnapshotCustomWrapStorage,
  markerId: Long,
  spec: MarkerSpec,
  initialRange: TextRange,
  override val indent: Int,
  override val priority: Int,
) : SnapshotRangeMarkerImpl(storage.document, markerId, spec, initialRange), CustomWrap {
  override val offset: Int
    get() = startOffset

  override fun currentRootReference(): AtomicReference<PMarkerRoot> = storage.rootReference(storage.currentSnapshot())

  override fun rootReference(snapshot: DocumentSnapshot): AtomicReference<PMarkerRoot> = storage.rootReference(snapshot)

  override fun afterDispose() {
    storage.afterDisposed(this)
  }

  override fun toString(): String = "SnapshotCustomWrap(offset=$offset, indent=$indent)"
}

private object CustomWrapMarkerPolicy : MarkerPolicy {
  override fun transform(
    entry: PMarkerRoot.MarkerEntry,
    patch: DocumentTextPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
  ): MarkerTransformResult {
    return when (val transformed = DefaultMarkerPolicy.transform(entry, patch, beforeText, afterText)) {
      is MarkerTransformResult.Invalid -> transformed
      is MarkerTransformResult.Valid -> validateOffset(transformed.entry, afterText)
    }
  }

  override fun afterRetarget(entry: PMarkerRoot.MarkerEntry, text: DocumentText): MarkerTransformResult {
    return validateOffset(entry, text)
  }

  private fun validateOffset(entry: PMarkerRoot.MarkerEntry, text: DocumentText): MarkerTransformResult {
    return if (isValidCustomWrapOffset(entry.startOffset, text)) {
      MarkerTransformResult.Valid(entry)
    }
    else {
      MarkerTransformResult.Invalid(
        reason = "The custom wrap reached an invalid offset",
        entry = entry,
      )
    }
  }
}

private fun isValidCustomWrapOffset(offset: Int, text: DocumentText): Boolean {
  if (offset < 0 || offset > text.length()) return false
  if (offset > 0 && offset < text.length()) {
    val chars = text.cachedChars()
    val previous = chars[offset - 1]
    if (previous == '\r' && chars[offset] == '\n' || Character.isHighSurrogate(previous) && Character.isLowSurrogate(chars[offset])) {
      return false
    }
  }
  val line = text.lineNumber(offset)
  return offset != text.lineStartOffset(line) && offset != text.lineEndOffset(line)
}
