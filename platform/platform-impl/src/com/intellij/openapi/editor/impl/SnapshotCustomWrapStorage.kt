// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.CustomWrap
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.impl.marker.DefaultMarkerPolicy
import com.intellij.openapi.editor.impl.marker.MarkerPolicy
import com.intellij.openapi.editor.impl.marker.MarkerSpec
import com.intellij.openapi.editor.impl.marker.MarkerTransformError
import com.intellij.openapi.editor.impl.marker.MarkerTransformResult
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerReference
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerRootStore
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.util.TextRangeScalarUtil
import java.util.concurrent.ConcurrentHashMap

/** Stores the snapshot custom wraps for one editor. */
internal class SnapshotCustomWrapStorage(
  private val model: CustomWrapModelImpl,
  val document: DocumentImpl,
) {
  private val registeredWraps: MutableSet<SnapshotMarkerReference<*>> = ConcurrentHashMap.newKeySet()
  val rootStore: SnapshotMarkerRootStore<SnapshotCustomWrap> = SnapshotMarkerRootStore.strong(
    document, SnapshotCustomWrap::class.java, onMarkersInvalidated = ::processInvalidatedWraps,
  )

  fun dispose() {
    rootStore.dispose(document.snapshotMarkerStores)
    registeredWraps.clear()
  }

  fun create(offset: Int, indent: Int, priority: Int): SnapshotCustomWrap {
    val snapshot = currentSnapshot()
    val spec = MarkerSpec.create(
      isGreedyToLeft = false,
      isGreedyToRight = false,
      isStickingToRight = false,
    )
    val wrap = SnapshotCustomWrap(this, spec, TextRangeScalarUtil.toScalarRange(offset, offset), indent, priority)
    check(registeredWraps.add(wrap.markerHandle)) { "Custom wrap ${wrap.id} is already registered" }
    rootStore.updateRoot(snapshot) {
      it.insert(wrap.markerHandle,
                offset,
                offset,
                spec,
                wrap.flavorFlags,
                policy = CustomWrapMarkerPolicy)
    }
    return wrap
  }

  fun remove(wrap: SnapshotCustomWrap): Boolean {
    if (wrap.storage !== this || wrap.markerHandle !in registeredWraps || !wrap.isValid) return false
    wrap.dispose()
    return true
  }

  fun collectAll(): List<CustomWrap> {
    val root = rootStore.root(currentSnapshot()) ?: return emptyList()
    val result = ArrayList<CustomWrap>()
    root.processAllRangeMarkers { entry, _ ->
      val wrap = entry.marker
      if (wrap is SnapshotCustomWrap) result.add(wrap)
      true
    }
    return result
  }

  fun collectInRange(startOffset: Int, endOffset: Int): List<CustomWrap> {
    val root = rootStore.root(currentSnapshot()) ?: return emptyList()
    val result = ArrayList<CustomWrap>()
    root.processRangeMarkersOverlappingWith(startOffset, endOffset, 0) { entry ->
      val wrap = entry.marker
      if (wrap is SnapshotCustomWrap) result.add(wrap)
      true
    }
    return result
  }

  fun hasWraps(): Boolean {
    val root = rootStore.root(currentSnapshot()) ?: return false
    return !root.isEmpty()
  }

  fun currentSnapshot(): DocumentSnapshot = document.core.snapshot()

  fun afterDisposed(wrap: SnapshotCustomWrap) {
    if (registeredWraps.remove(wrap.markerHandle)) model.notifyRemoved(wrap)
  }

  private fun processInvalidatedWraps(invalidatedHandles: List<SnapshotMarkerReference<SnapshotCustomWrap>>) {
    val size = invalidatedHandles.size
    for (i in 0 until size) {
      val handle = invalidatedHandles[i]
      if (registeredWraps.remove(handle)) model.notifyRemoved(handle.get())
    }
  }
}

internal class SnapshotCustomWrap(
  internal val storage: SnapshotCustomWrapStorage,
  spec: Byte,
  initialRange: Long,
  override val indent: Int,
  override val priority: Int,
) : SnapshotRangeMarkerImpl(
  storage.document, storage.rootStore, spec, initialRange,
), CustomWrap {
  override val offset: Int
    get() = startOffset

  override fun afterDispose() {
    storage.afterDisposed(this)
  }

  override fun toString(): String = "SnapshotCustomWrap(offset=$offset, indent=$indent)"
}

private object CustomWrapMarkerPolicy : MarkerPolicy {
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
      validateOffset(transformed, afterText)
    }
  }

  override fun afterRetarget(entry: PMarkerRoot.MarkerEntry, text: DocumentText): MarkerTransformResult {
    return validateOffset(MarkerTransformResult(entry), text)
  }

  private fun validateOffset(range: MarkerTransformResult, text: DocumentText): MarkerTransformResult {
    return if (isValidCustomWrapOffset(range.startOffset, text)) {
      range
    }
    else {
      MarkerTransformResult(MarkerTransformError.INVALID_CUSTOM_WRAP_OFFSET)
    }
  }
}

private fun isValidCustomWrapOffset(offset: Int, text: DocumentText): Boolean {
  if (offset < 0 || offset > text.length()) return false
  if (offset > 0 && offset < text.length()) {
    val chars = text.luckyChars()
    val previous = chars[offset - 1]
    if (previous == '\r' && chars[offset] == '\n' || Character.isHighSurrogate(previous) && Character.isLowSurrogate(chars[offset])) {
      return false
    }
  }
  val line = text.lineNumber(offset)
  return offset != text.lineStartOffset(line) && offset != text.lineEndOffset(line)
}
