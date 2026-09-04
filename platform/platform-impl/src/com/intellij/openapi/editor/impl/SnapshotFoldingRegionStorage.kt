// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.CustomFoldRegionRendererEx
import com.intellij.openapi.editor.CustomFoldRegionRenderer
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.editor.InlayModel
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
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.util.Processor
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ConcurrentLongObjectMap
import com.intellij.util.containers.Java11Shim
import it.unimi.dsi.fastutil.longs.LongList
import java.awt.Point
import java.util.concurrent.atomic.AtomicReference

private const val FOLD_REGION_FLAVOR: Int = 1
private const val CUSTOM_FOLD_REGION_FLAVOR: Int = 2

/** Stores fold regions for one editor in a snapshot marker root. */
internal class SnapshotFoldingRegionStorage(
  internal val model: FoldingModelImpl,
  internal val editor: EditorImpl,
  val document: DocumentImpl,
) : FoldingRegionStorage {
  private val regionsById: ConcurrentLongObjectMap<SnapshotFoldRegion> = Java11Shim.createConcurrentLongObjectMap()
  private val rootStore = SnapshotMarkerRootStore(
    document,
    onMarkersInvalidated = ::processInvalidatedRegions,
    onDocumentChanged = ::documentChanged,
  )
  private var sizesBeforeUpdate: Map<Long, Int> = emptyMap()

  override fun createFoldRegion(
    startOffset: Int,
    endOffset: Int,
    placeholder: String,
    group: FoldingGroup?,
    neverExpands: Boolean,
  ): FoldRegionMarker {
    val markerId = SnapshotMarkerEngineImpl.nextMarkerId()
    val spec = MarkerSpec(isGreedyToLeft = false, isGreedyToRight = false, policy = FoldRegionMarkerPolicy)
    val region = SnapshotFoldRegion(
      storage = this,
      markerId = markerId,
      spec = spec,
      initialRange = TextRange(startOffset, endOffset),
      placeholder = placeholder,
      group = group,
      neverExpands = neverExpands,
    )
    return register(region, startOffset, endOffset, spec)
  }

  override fun createCustomFoldRegion(
    startOffset: Int,
    endOffset: Int,
    renderer: CustomFoldRegionRenderer,
  ): CustomFoldRegionMarker {
    val markerId = SnapshotMarkerEngineImpl.nextMarkerId()
    val spec = MarkerSpec(isGreedyToLeft = false, isGreedyToRight = false, policy = CustomFoldRegionMarkerPolicy)
    val region = SnapshotCustomFoldRegion(
      storage = this,
      markerId = markerId,
      spec = spec,
      initialRange = TextRange(startOffset, endOffset),
      renderer = renderer,
    )
    return register(region, startOffset, endOffset, spec)
  }

  override fun removeRegion(region: FoldRegionMarker): Boolean {
    if (region !is SnapshotFoldRegion || region.storage !== this || regionsById.get(region.id) !== region || !region.isValid) return false
    model.notifyBeforeFoldRegionDisposed(region)
    val removed = SnapshotMarkerEngineImpl.removeRangeMarker(region)
    regionsById.remove(region.id, region)
    return removed
  }

  override fun clearRegions() {
    collectRegions(0, document.textLength).forEach(::removeRegion)
  }

  override fun dispose() {
    rootStore.dispose()
    regionsById.clear()
  }

  override fun size(): Int = regionsById.size()

  override fun processAllRegions(processor: Processor<in FoldRegionMarker>): Boolean {
    return processRegionsOverlappingWith(0, document.textLength, processor)
  }

  override fun processRegionsContaining(offset: Int, processor: Processor<in FoldRegionMarker>): Boolean {
    return processRegionsOverlappingWith(offset, offset) { region ->
      if (region.startOffset <= offset && offset <= region.endOffset) {
        processor.process(region)
      }
      else {
        true
      }
    }
  }

  override fun processRegionsOverlappingWith(
    startOffset: Int,
    endOffset: Int,
    processor: Processor<in FoldRegionMarker>,
  ): Boolean {
    for (region in collectRegions(startOffset, endOffset)) {
      if (!processor.process(region)) return false
    }
    return true
  }

  override fun beforeDocumentChange(event: DocumentEvent) {
    val changeStart = event.offset
    val changeEnd = changeStart + event.oldLength
    val sizes = if (event.oldLength > 0) HashMap<Long, Int>() else null
    processRegionsOverlappingWith(changeStart, changeEnd) { region ->
      region as SnapshotFoldRegion
      if (changeStart < region.endOffset && changeEnd > region.startOffset) {
        region.markDocumentRegionChanged()
      }
      if (sizes != null) {
        val size = if (region.isExpanded) 0 else region.endOffset - region.startOffset
        sizes[region.id] = size
      }
      true
    }
    sizesBeforeUpdate = sizes ?: emptyMap()
  }

  fun rootReference(snapshot: DocumentSnapshot): AtomicReference<PMarkerRoot> = rootStore.rootReference(snapshot)

  fun currentSnapshot(): DocumentSnapshot = document.core.snapshot()

  private fun <T : SnapshotFoldRegion> register(
    region: T,
    startOffset: Int,
    endOffset: Int,
    spec: MarkerSpec,
  ): T {
    check(regionsById.putIfAbsent(region.id, region) == null) { "Fold region ${region.id} is already registered" }
    val markerReference = SnapshotMarkerEngineImpl.createMarkerReference(region, retainStrong = true)
    rootStore.updateRoot(currentSnapshot()) {
      it.insert(region.id, startOffset, endOffset, spec, region.flavorFlags, markerReference)
    }
    return region
  }

  private fun documentChanged(@Suppress("UNUSED_PARAMETER") event: DocumentEvent) {
    removeDuplicateRegions()
    for (region in collectRegions(0, document.textLength, CUSTOM_FOLD_REGION_FLAVOR)) {
      model.addAffectedCustomRegions(region as SnapshotCustomFoldRegion)
    }
    sizesBeforeUpdate = emptyMap()
  }

  private fun removeDuplicateRegions() {
    val regions = collectRegions(0, document.textLength)
    var index = 0
    while (index < regions.size) {
      val first = regions[index]
      var endIndex = index + 1
      while (endIndex < regions.size && sameRegionKindAndRange(first, regions[endIndex])) {
        endIndex++
      }
      if (endIndex - index > 1) {
        var winner = first
        for (candidateIndex in index + 1 until endIndex) {
          val candidate = regions[candidateIndex]
          if (sizeBeforeUpdate(candidate) > sizeBeforeUpdate(winner)) winner = candidate
        }
        for (candidateIndex in index until endIndex) {
          val region = regions[candidateIndex]
          if (region !== winner) invalidateDuplicate(region)
        }
      }
      index = endIndex
    }
  }

  private fun invalidateDuplicate(region: SnapshotFoldRegion) {
    rootStore.updateRoot(currentSnapshot()) { it.remove(region.id) }
    if (regionsById.remove(region.id, region)) model.snapshotFoldRegionInvalidated(region)
  }

  private fun sizeBeforeUpdate(region: SnapshotFoldRegion): Int = sizesBeforeUpdate[region.id] ?: 0

  private fun processInvalidatedRegions(markerIds: LongList) {
    val size = markerIds.size
    for (index in 0 until size) {
      val markerId = markerIds.getLong(index)
      val region = regionsById.get(markerId) ?: continue
      if (regionsById.remove(markerId, region)) model.snapshotFoldRegionInvalidated(region)
    }
  }

  private fun collectRegions(
    startOffset: Int,
    endOffset: Int,
    tastePreference: Int = 0,
  ): List<SnapshotFoldRegion> {
    val snapshot = currentSnapshot()
    val root = rootStore.root(snapshot) ?: return emptyList()
    val regions = ArrayList<SnapshotFoldRegion>()
    root.processRangeMarkersOverlappingWith(startOffset, endOffset, tastePreference) { entry ->
      val region = entry.markerReference?.get() as? SnapshotFoldRegion
      if (region != null) regions.add(region)
      true
    }
    regions.sortWith(REGION_COMPARATOR)
    return regions
  }

  companion object {
    private val REGION_COMPARATOR = compareBy<SnapshotFoldRegion>(
      { it.startOffset },
      { it.endOffset - it.startOffset },
      { it is SnapshotCustomFoldRegion },
      { it.id },
    )

    private fun sameRegionKindAndRange(first: SnapshotFoldRegion, second: SnapshotFoldRegion): Boolean {
      return first.startOffset == second.startOffset &&
             first.endOffset == second.endOffset &&
             (first is SnapshotCustomFoldRegion) == (second is SnapshotCustomFoldRegion)
    }
  }
}

internal open class SnapshotFoldRegion(
  internal val storage: SnapshotFoldingRegionStorage,
  markerId: Long,
  spec: MarkerSpec,
  initialRange: TextRange,
  private var placeholder: String,
  private val group: FoldingGroup?,
  private val neverExpands: Boolean,
) : SnapshotRangeMarkerImpl(storage.document, markerId, spec, initialRange), FoldRegionMarker {
  protected val editorImpl: EditorImpl
    get() = storage.editor

  private var expanded: Boolean = true
  private var documentRegionChanged: Boolean = false

  override fun getFlavorFlags(): Byte = FOLD_REGION_FLAVOR.toByte()

  override fun isExpanded(): Boolean = expanded

  @RequiresEdt
  override fun setExpanded(expanded: Boolean) {
    setExpanded(expanded, true)
  }

  @RequiresEdt
  override fun setExpanded(expanded: Boolean, notify: Boolean) {
    val foldingModel = storage.model
    val group = group
    if (group == null) {
      setExpanded(expanded, foldingModel, this, notify)
      return
    }
    for (region in foldingModel.getGroupedRegions(group)) {
      setExpanded(expanded, foldingModel, region, notify || region !== this)
      if (region.isExpanded != expanded) {
        for (regionToRevert in foldingModel.getGroupedRegions(group)) {
          if (regionToRevert === region) break
          setExpanded(!expanded, foldingModel, regionToRevert, notify || region !== this)
        }
        return
      }
    }
  }

  override fun setExpandedInternal(expanded: Boolean) {
    this.expanded = expanded
  }

  override fun isValid(): Boolean = super.isValid() && startOffset < endOffset

  override fun getPlaceholderText(): String = placeholder

  override fun getEditor(): EditorImpl = editorImpl

  override fun getGroup(): FoldingGroup? = group

  override fun shouldNeverExpand(): Boolean = neverExpands

  override fun hasDocumentRegionChanged(): Boolean = documentRegionChanged

  override fun markDocumentRegionChanged() {
    documentRegionChanged = true
  }

  override fun resetDocumentRegionChanged() {
    documentRegionChanged = false
  }

  override fun setGreedyToLeft(greedy: Boolean) {
  }

  override fun setGreedyToRight(greedy: Boolean) {
  }

  override fun setStickingToRight(value: Boolean) {
  }

  override fun setInnerHighlightersMuted(value: Boolean) {
    putUserData(MUTE_INNER_HIGHLIGHTERS, if (value) true else null)
  }

  override fun areInnerHighlightersMuted(): Boolean = getUserData(MUTE_INNER_HIGHLIGHTERS) == true

  override fun setGutterMarkEnabledForSingleLine(value: Boolean) {
    if (value == isGutterMarkEnabledForSingleLine) return
    putUserData(SHOW_GUTTER_MARK_FOR_SINGLE_LINE, if (value) true else null)
    editorImpl.gutterComponentEx.repaint()
  }

  override fun isGutterMarkEnabledForSingleLine(): Boolean = getUserData(SHOW_GUTTER_MARK_FOR_SINGLE_LINE) == true

  override fun setPlaceholderText(text: String) {
    placeholder = text
    storage.model.onPlaceholderTextChanged(this)
  }

  override fun dispose() {
    storage.model.removeRegionFromTree(this)
  }

  override fun currentRootReference(): AtomicReference<PMarkerRoot> = storage.rootReference(storage.currentSnapshot())

  override fun rootReference(snapshot: DocumentSnapshot): AtomicReference<PMarkerRoot> = storage.rootReference(snapshot)

  override fun toString(): String {
    return "FoldRegion ${if (expanded) "-" else "+"}($startOffset:$endOffset)" +
           (if (isValid) "" else "(invalid)") + ", placeholder='$placeholder'"
  }

  companion object {
    private val MUTE_INNER_HIGHLIGHTERS = Key.create<Boolean>("mute.inner.highlighters")
    private val SHOW_GUTTER_MARK_FOR_SINGLE_LINE = Key.create<Boolean>("show.gutter.mark.for.single.line")

    private fun setExpanded(
      expanded: Boolean,
      foldingModel: FoldingModelImpl,
      region: FoldRegion,
      notify: Boolean,
    ) {
      if (expanded) {
        foldingModel.expandFoldRegion(region, notify)
      }
      else {
        foldingModel.collapseFoldRegion(region, notify)
      }
    }
  }
}

internal class SnapshotCustomFoldRegion(
  storage: SnapshotFoldingRegionStorage,
  markerId: Long,
  spec: MarkerSpec,
  initialRange: TextRange,
  private val renderer: CustomFoldRegionRenderer,
) : SnapshotFoldRegion(storage, markerId, spec, initialRange, "", null, true), CustomFoldRegionMarker {
  private var width: Int = 0
  private var height: Int = 0
  private var gutterIconRenderer: GutterIconRenderer? = null

  init {
    updateProperties()
  }

  override fun getFlavorFlags(): Byte = CUSTOM_FOLD_REGION_FLAVOR.toByte()

  override fun getRenderer(): CustomFoldRegionRenderer = renderer

  override fun getWidthInPixels(): Int = width

  override fun getHeightInPixels(): Int = height

  override fun getGutterIconRenderer(): GutterIconRenderer? = gutterIconRenderer

  override fun update() {
    ThreadingAssertions.assertEventDispatchThread()
    if (editorImpl.isDisposed || !isValid) return
    if (editorImpl.myDocumentChangeInProgress) {
      throw IllegalStateException("A custom fold region cannot update during a document change")
    }
    val oldWidth = width
    val oldHeight = height
    val oldGutterIconRenderer = gutterIconRenderer
    updateProperties()
    var changeFlags = 0
    if (oldWidth != width) changeFlags = changeFlags or InlayModel.ChangeFlags.WIDTH_CHANGED
    if (oldHeight != height) changeFlags = changeFlags or InlayModel.ChangeFlags.HEIGHT_CHANGED
    if (oldGutterIconRenderer != gutterIconRenderer) changeFlags = changeFlags or InlayModel.ChangeFlags.GUTTER_ICON_PROVIDER_CHANGED
    if (changeFlags == 0) {
      repaint()
    }
    else {
      storage.model.onCustomFoldRegionPropertiesChange(this, changeFlags)
    }
  }

  override fun repaint() {
    if (!isValid || editorImpl.isDisposed) return
    if (storage.model.isInBatchFoldingOperation) {
      storage.model.setRepaintRequested(true)
      return
    }
    val component = editorImpl.contentComponent
    if (!component.isShowing) return
    val location = location ?: return
    component.repaint(0, location.y, component.width, height)
  }

  override fun getLocation(): Point? {
    val startOffset = startOffset
    val visibleRegion = storage.model.getCollapsedRegionAtOffset(startOffset)
    return if (visibleRegion === this) {
      Point(editorImpl.contentComponent.insets.left, editorImpl.visualLineToY(editorImpl.offsetToVisualLine(startOffset)))
    }
    else {
      null
    }
  }

  private fun updateProperties() {
    width = renderer.calcWidthInPixels(this).coerceAtLeast(0)
    height = if (renderer is CustomFoldRegionRendererEx) {
      renderer.calcHeightInPixels(this).coerceAtLeast(renderer.getMinimumHeightInPixels())
    }
    else {
      renderer.calcHeightInPixels(this).coerceAtLeast(editorImpl.lineHeight)
    }
    gutterIconRenderer = renderer.calcGutterIconRenderer(this)
  }

  override fun toString(): String = "${super.toString()}, renderer: $renderer, size: ${width}x$height"
}

private object FoldRegionMarkerPolicy : MarkerPolicy {
  override fun transform(
    entry: PMarkerRoot.MarkerEntry,
    patch: DocumentTextPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
  ): MarkerTransformResult {
    return when (val transformed = DefaultMarkerPolicy.transform(entry, patch, beforeText, afterText)) {
      is MarkerTransformResult.Invalid -> transformed
      is MarkerTransformResult.Valid -> validateRange(alignToCharacterBoundaries(transformed.entry, afterText))
    }
  }

  override fun afterRetarget(entry: PMarkerRoot.MarkerEntry, text: DocumentText): MarkerTransformResult {
    return validateRange(alignToCharacterBoundaries(entry, text))
  }

  private fun validateRange(entry: PMarkerRoot.MarkerEntry): MarkerTransformResult {
    return if (entry.startOffset < entry.endOffset) {
      MarkerTransformResult.Valid(entry)
    }
    else {
      MarkerTransformResult.Invalid("The fold region became empty", entry)
    }
  }
}

private object CustomFoldRegionMarkerPolicy : MarkerPolicy {
  override fun transform(
    entry: PMarkerRoot.MarkerEntry,
    patch: DocumentTextPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
  ): MarkerTransformResult {
    return when (val transformed = DefaultMarkerPolicy.transform(entry, patch, beforeText, afterText)) {
      is MarkerTransformResult.Invalid -> transformed
      is MarkerTransformResult.Valid -> validateLineBoundaries(transformed.entry, afterText)
    }
  }

  override fun afterRetarget(entry: PMarkerRoot.MarkerEntry, text: DocumentText): MarkerTransformResult {
    return validateLineBoundaries(entry, text)
  }

  private fun validateLineBoundaries(entry: PMarkerRoot.MarkerEntry, text: DocumentText): MarkerTransformResult {
    if (entry.startOffset >= entry.endOffset) {
      return MarkerTransformResult.Invalid("The custom fold region became empty", entry)
    }
    val startLine = text.lineNumber(entry.startOffset)
    val endLine = text.lineNumber(entry.endOffset)
    return if (entry.startOffset == text.lineStartOffset(startLine) && entry.endOffset == text.lineEndOffset(endLine)) {
      MarkerTransformResult.Valid(entry)
    }
    else {
      MarkerTransformResult.Invalid("The custom fold region left its line boundaries", entry)
    }
  }
}

private fun alignToCharacterBoundaries(entry: PMarkerRoot.MarkerEntry, text: DocumentText): PMarkerRoot.MarkerEntry {
  val startOffset = if (isInsideCharacterPair(entry.startOffset, text)) entry.startOffset - 1 else entry.startOffset
  val endOffset = if (isInsideCharacterPair(entry.endOffset, text)) entry.endOffset - 1 else entry.endOffset
  return if (startOffset == entry.startOffset && endOffset == entry.endOffset) {
    entry
  }
  else {
    entry.copy(startOffset = startOffset, endOffset = endOffset)
  }
}

private fun isInsideCharacterPair(offset: Int, text: DocumentText): Boolean {
  if (offset <= 0 || offset >= text.length()) return false
  val chars = text.cachedChars()
  val previous = chars[offset - 1]
  return previous == '\r' && chars[offset] == '\n' || Character.isHighSurrogate(previous) && Character.isLowSurrogate(chars[offset])
}
