// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.impl.InlayKeys.ID_BEFORE_DISPOSAL
import com.intellij.openapi.editor.impl.InlayKeys.OFFSET_BEFORE_DISPOSAL
import com.intellij.openapi.editor.impl.InlayKeys.ORDER_BEFORE_DISPOSAL
import com.intellij.openapi.editor.impl.marker.DefaultMarkerPolicy
import com.intellij.openapi.editor.impl.marker.MarkerPolicy
import com.intellij.openapi.editor.impl.marker.MarkerSpec
import com.intellij.openapi.editor.impl.marker.MarkerTransformResult
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerRootStore
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.util.DocumentEventUtil
import com.intellij.util.Processor
import com.intellij.util.containers.ConcurrentLongObjectMap
import com.intellij.util.containers.Java11Shim
import it.unimi.dsi.fastutil.longs.LongList
import it.unimi.dsi.fastutil.longs.LongLists
import java.util.concurrent.atomic.AtomicReference

private const val INLINE_FLAVOR: Int = 1
private const val AFTER_LINE_END_FLAVOR: Int = 2
private const val BLOCK_FLAVOR: Int = 4

/** Stores inline, after-line-end, and block inlays for one editor. */
internal class SnapshotInlayStorage(
  private val model: InlayModelImpl,
  private val editor: EditorImpl,
  val document: DocumentImpl,
) {
  private val markersById: ConcurrentLongObjectMap<SnapshotInlayMarker<*>> = Java11Shim.createConcurrentLongObjectMap()
  private val rootStore = SnapshotMarkerRootStore(
    document,
    onMarkersInvalidated = ::saveInvalidatedMarkers,
    onDocumentChanged = ::processInvalidatedMarkers,
  )
  private var invalidatedMarkerIds: LongList = LongLists.EMPTY_LIST
  private var querySnapshot: DocumentSnapshot? = null

  fun <R : EditorCustomElementRenderer> createInline(
    offset: Int,
    relatesToPrecedingText: Boolean,
    priority: Int,
    renderer: R,
  ): SnapshotInlineInlayMarker<R> {
    val markerId = SnapshotMarkerEngineImpl.nextMarkerId()
    val spec = MarkerSpec(
      isGreedyToLeft = false,
      isGreedyToRight = false,
      isStickingToRight = relatesToPrecedingText,
      policy = InlineInlayMarkerPolicy,
    )
    val marker = SnapshotInlineInlayMarker(
      storage = this,
      editor = editor,
      markerId = markerId,
      spec = spec,
      initialRange = TextRange(offset, offset),
      relatesToPrecedingText = relatesToPrecedingText,
      priority = priority,
      renderer = renderer,
    )
    return register(marker, offset, spec)
  }

  fun <R : EditorCustomElementRenderer> createAfterLineEnd(
    offset: Int,
    relatesToPrecedingText: Boolean,
    softWrappable: Boolean,
    priority: Int,
    renderer: R,
  ): SnapshotAfterLineEndInlayMarker<R> {
    val markerId = SnapshotMarkerEngineImpl.nextMarkerId()
    val spec = MarkerSpec(
      isGreedyToLeft = false,
      isGreedyToRight = false,
      isStickingToRight = relatesToPrecedingText,
    )
    val marker = SnapshotAfterLineEndInlayMarker(
      storage = this,
      editor = editor,
      markerId = markerId,
      spec = spec,
      initialRange = TextRange(offset, offset),
      relatesToPrecedingText = relatesToPrecedingText,
      softWrappable = softWrappable,
      priority = priority,
      renderer = renderer,
    )
    return register(marker, offset, spec)
  }

  fun <R : EditorCustomElementRenderer> createBlock(
    offset: Int,
    relatesToPrecedingText: Boolean,
    showAbove: Boolean,
    showWhenFolded: Boolean,
    priority: Int,
    renderer: R,
  ): SnapshotBlockInlayMarker<R> {
    val markerId = SnapshotMarkerEngineImpl.nextMarkerId()
    val spec = MarkerSpec(
      isGreedyToLeft = false,
      isGreedyToRight = false,
      isStickingToRight = relatesToPrecedingText,
    )
    val marker = SnapshotBlockInlayMarker(
      storage = this,
      editor = editor,
      markerId = markerId,
      spec = spec,
      initialRange = TextRange(offset, offset),
      relatesToPrecedingText = relatesToPrecedingText,
      showAbove = showAbove,
      showWhenFolded = showWhenFolded,
      priority = priority,
      renderer = renderer,
    )
    return register(marker, offset, spec, marker.heightInPixels)
  }

  fun beforeDocumentChange(event: DocumentEvent) {
    querySnapshot = currentSnapshot()
    if (event.oldLength == 0 || event.newLength != 0) return

    val markersAtStart = collectInline(event.offset, event.offset)
    val oldEndOffset = event.offset + event.oldLength
    val markersAtEnd = collectInline(oldEndOffset, oldEndOffset)
    (markersAtStart + markersAtEnd).forEachIndexed { index, inlay ->
      inlay.iterationOrder = index.toLong()
    }
  }

  fun collectInline(startOffset: Int, endOffset: Int): List<SnapshotInlineInlayMarker<*>> {
    return collect(startOffset, endOffset, INLINE_FLAVOR) { it as? SnapshotInlineInlayMarker<*> }
  }

  fun collectAfterLineEnd(startOffset: Int, endOffset: Int): List<SnapshotAfterLineEndInlayMarker<*>> {
    return collect(startOffset, endOffset, AFTER_LINE_END_FLAVOR) { it as? SnapshotAfterLineEndInlayMarker<*> }
  }

  fun collectBlock(startOffset: Int, endOffset: Int): List<BlockInlay<*>> {
    return collect(startOffset, endOffset, BLOCK_FLAVOR) { it as? BlockInlay<*> }
  }

  fun hasInline(startOffset: Int, endOffset: Int): Boolean = has(startOffset, endOffset, INLINE_FLAVOR)

  fun hasAfterLineEnd(): Boolean = has(0, document.textLength, AFTER_LINE_END_FLAVOR)

  fun hasBlock(): Boolean = has(0, document.textLength, BLOCK_FLAVOR)

  fun processBlock(startOffset: Int, endOffset: Int, processor: Processor<in BlockInlay<*>>): Boolean {
    val root = rootStore.root(snapshotForQueries()) ?: return true
    return root.processRangeMarkersOverlappingWith(startOffset, endOffset, BLOCK_FLAVOR) { entry ->
      val marker = entry.markerReference?.get() as? BlockInlay<*> ?: return@processRangeMarkersOverlappingWith true
      processor.process(marker)
    }
  }

  fun getBlockHeightUpToOffset(offset: Int): Int {
    return rootStore.root(snapshotForQueries())?.getPrefixAggregate(offset) ?: 0
  }

  fun allInlays(): List<EditorInlay<*>> {
    return collect(0, document.textLength, 0) { it }
  }

  fun dispose() {
    allInlays().forEach(Disposer::dispose)
    rootStore.dispose()
    markersById.clear()
  }

  fun rootReference(snapshot: DocumentSnapshot): AtomicReference<PMarkerRoot> = rootStore.rootReference(snapshot)

  fun currentSnapshot(): DocumentSnapshot = document.core.snapshot()

  fun afterDisposed(marker: SnapshotInlayMarker<*>) {
    markersById.remove(marker.id, marker)
  }

  fun updateBlockHeight(marker: SnapshotBlockInlayMarker<*>, heightInPixels: Int) {
    rootStore.updateRoot(currentSnapshot()) { it.updateMeasure(marker.id, heightInPixels) }
  }

  private fun <T : SnapshotInlayMarker<*>> register(marker: T, offset: Int, spec: MarkerSpec, measure: Int = 0): T {
    check(markersById.putIfAbsent(marker.id, marker) == null) { "Inlay marker ${marker.id} is already registered" }
    val markerReference = SnapshotMarkerEngineImpl.createMarkerReference(marker, retainStrong = true)
    rootStore.updateRoot(currentSnapshot()) {
      it.insert(marker.id, offset, offset, spec, marker.flavorFlags, markerReference, measure)
    }
    return marker
  }

  private fun saveInvalidatedMarkers(markerIds: LongList) {
    invalidatedMarkerIds = markerIds
  }

  private fun processInvalidatedMarkers(event: DocumentEvent) {
    querySnapshot = null
    val markerIds = invalidatedMarkerIds
    invalidatedMarkerIds = LongLists.EMPTY_LIST
    val size = markerIds.size
    for (index in 0 until size) {
      val markerId = markerIds.getLong(index)
      val marker = markersById.get(markerId) ?: continue
      if (!markersById.remove(markerId, marker)) continue
      val delayNotification = marker is SnapshotInlineInlayMarker<*> && DocumentEventUtil.isMoveInsertion(event)
      model.snapshotInlayInvalidated(marker, delayNotification)
    }
  }

  private fun has(startOffset: Int, endOffset: Int, flavor: Int): Boolean {
    val root = rootStore.root(snapshotForQueries()) ?: return false
    var found = false
    root.processRangeMarkersOverlappingWith(startOffset, endOffset, flavor) {
      found = true
      false
    }
    return found
  }

  private fun <T> collect(
    startOffset: Int,
    endOffset: Int,
    flavor: Int,
    convert: (SnapshotInlayMarker<*>) -> T?,
  ): List<T> {
    val root = rootStore.root(snapshotForQueries()) ?: return emptyList()
    val markers = ArrayList<SnapshotInlayMarker<*>>()
    root.processRangeMarkersOverlappingWith(startOffset, endOffset, flavor) { entry ->
      val marker = entry.markerReference?.get() as? SnapshotInlayMarker<*>
      if (marker != null) markers.add(marker)
      true
    }
    markers.sortWith(compareBy<SnapshotInlayMarker<*>>({ it.startOffset }, { it.iterationOrder }))
    return markers.mapNotNull(convert)
  }

  private fun snapshotForQueries(): DocumentSnapshot = querySnapshot ?: currentSnapshot()
}

internal abstract class SnapshotInlayMarker<R : EditorCustomElementRenderer>(
  private val storage: SnapshotInlayStorage,
  private val editor: EditorImpl,
  markerId: Long,
  spec: MarkerSpec,
  initialRange: TextRange,
  private val relatesToPrecedingText: Boolean,
  private val renderer: R,
) : SnapshotRangeMarkerImpl(storage.document, markerId, spec, initialRange), EditorInlay<R> {
  private var widthInPixels: Int = 0

  @Volatile
  var iterationOrder: Long = markerId

  override fun getEditorImpl(): EditorImpl = editor

  override fun getRenderer(): R = renderer

  override fun isRelatedToPrecedingText(): Boolean = relatesToPrecedingText

  override fun getWidthInPixels(): Int = widthInPixels

  override fun setWidthInPixels(widthInPixels: Int) {
    this.widthInPixels = widthInPixels
  }

  override fun isValid(): Boolean = !editor.isDisposed && super.isValid()

  final override fun currentRootReference(): AtomicReference<PMarkerRoot> = storage.rootReference(storage.currentSnapshot())

  final override fun rootReference(snapshot: DocumentSnapshot): AtomicReference<PMarkerRoot> = storage.rootReference(snapshot)

  final override fun afterDispose() {
    storage.afterDisposed(this)
  }

  final override fun dispose() {
    EditorImpl.assertIsDispatchThread()
    if (!isValid()) return

    beforeInlayDispose()
    val offset = getOffset()
    putUserData(OFFSET_BEFORE_DISPOSAL, offset)
    putUserData(ID_BEFORE_DISPOSAL, id)
    super.dispose()
    Disposer.dispose(this)
    editor.inlayModel.notifyRemoved(this)
  }

  protected open fun beforeInlayDispose() {
  }
}

internal class SnapshotInlineInlayMarker<R : EditorCustomElementRenderer>(
  storage: SnapshotInlayStorage,
  editor: EditorImpl,
  markerId: Long,
  spec: MarkerSpec,
  initialRange: TextRange,
  relatesToPrecedingText: Boolean,
  private val priority: Int,
  renderer: R,
) : SnapshotInlayMarker<R>(storage, editor, markerId, spec, initialRange, relatesToPrecedingText, renderer), InlineInlay<R> {
  init {
    doUpdate()
  }

  override fun getFlavorFlags(): Byte = INLINE_FLAVOR.toByte()

  override fun getPriority(): Int = priority

  override fun getOrder(): Int = getUserData(ORDER_BEFORE_DISPOSAL) ?: -1

  override fun beforeInlayDispose() {
    val offset = getOffset()
    val inlays = getEditorImpl().inlayModel.getInlineElementsInRange(offset, offset)
    putUserData(ORDER_BEFORE_DISPOSAL, inlays.indexOf(this))
  }

  override fun toString(): String {
    return "[Inline inlay, offset=${getOffset()}, width=${getWidthInPixels()}, renderer=${getRenderer()}]" +
           if (isValid()) "" else "(invalid)"
  }
}

internal class SnapshotAfterLineEndInlayMarker<R : EditorCustomElementRenderer>(
  storage: SnapshotInlayStorage,
  editor: EditorImpl,
  markerId: Long,
  spec: MarkerSpec,
  initialRange: TextRange,
  relatesToPrecedingText: Boolean,
  private val softWrappable: Boolean,
  private val priority: Int,
  renderer: R,
) : SnapshotInlayMarker<R>(storage, editor, markerId, spec, initialRange, relatesToPrecedingText, renderer), AfterLineEndInlay<R> {
  private val order: Int = nextOrder()

  init {
    doUpdate()
  }

  override fun getFlavorFlags(): Byte = AFTER_LINE_END_FLAVOR.toByte()

  override fun isSoftWrappable(): Boolean = softWrappable

  override fun getPriority(): Int = priority

  override fun getOrder(): Int = order

  override fun toString(): String {
    return "[After-line-end inlay, offset=${getOffset()}, width=${getWidthInPixels()}, renderer=${getRenderer()}]" +
           if (isValid()) "" else "(invalid)"
  }

  companion object {
    private var globalCounter: Int = 0

    private fun nextOrder(): Int = globalCounter++
  }
}

internal class SnapshotBlockInlayMarker<R : EditorCustomElementRenderer>(
  private val storage: SnapshotInlayStorage,
  editor: EditorImpl,
  markerId: Long,
  spec: MarkerSpec,
  initialRange: TextRange,
  relatesToPrecedingText: Boolean,
  private val showAbove: Boolean,
  private val showWhenFolded: Boolean,
  private val priority: Int,
  renderer: R,
) : SnapshotInlayMarker<R>(storage, editor, markerId, spec, initialRange, relatesToPrecedingText, renderer), BlockInlay<R> {
  private var height: Int = 0
  private var gutterIconRenderer: GutterIconRenderer? = null

  init {
    doUpdate()
  }

  override fun getFlavorFlags(): Byte = BLOCK_FLAVOR.toByte()

  override fun getPriority(): Int = priority

  override fun isShownAbove(): Boolean = showAbove

  override fun isShownWhenFolded(): Boolean = showWhenFolded

  override fun getHeightInPixels(): Int = height

  override fun setHeightInPixels(heightInPixels: Int) {
    if (height == heightInPixels) return
    height = heightInPixels
    storage.updateBlockHeight(this, heightInPixels)
  }

  override fun getGutterIconRenderer(): GutterIconRenderer? = gutterIconRenderer

  override fun setGutterIconRenderer(gutterIconRenderer: GutterIconRenderer?) {
    this.gutterIconRenderer = gutterIconRenderer
  }

  override fun toString(): String {
    return "[Block inlay, offset=${getOffset()}, width=${getWidthInPixels()}, height=$height, renderer=${getRenderer()}]" +
           if (isValid()) "" else "(invalid)"
  }
}

private object InlineInlayMarkerPolicy : MarkerPolicy {
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
    val offset = entry.startOffset
    if (offset <= 0 || offset >= text.length()) return MarkerTransformResult.Valid(entry)
    val chars = text.cachedChars()
    return if (Character.isHighSurrogate(chars[offset - 1]) && Character.isLowSurrogate(chars[offset])) {
      MarkerTransformResult.Invalid("The inline inlay reached a surrogate pair", entry)
    }
    else {
      MarkerTransformResult.Valid(entry)
    }
  }
}
