// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.CustomFoldRegionRendererEx
import com.intellij.openapi.editor.CustomFoldRegionRenderer
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.impl.marker.DefaultMarkerPolicy
import com.intellij.openapi.editor.impl.marker.MarkerPolicy
import com.intellij.openapi.editor.impl.marker.MarkerSpec
import com.intellij.openapi.editor.impl.marker.MarkerTransformError
import com.intellij.openapi.editor.impl.marker.MarkerTransformResult
import com.intellij.openapi.editor.impl.marker.PMarkerResolution
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerReference
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerRootStore
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.util.Processor
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Point
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap

private const val FOLD_REGION_FLAVOR: Int = 1
private const val CUSTOM_FOLD_REGION_FLAVOR: Int = 2

/** Stores fold regions for one editor in the snapshot marker [rootStore]. */
internal class SnapshotFoldingRegionStorage(
  internal val model: FoldingModelImpl,
  internal val editor: EditorImpl,
  val document: DocumentImpl,
) : FoldingRegionStorage {
  private val registeredRegions: MutableSet<SnapshotMarkerReference<*>> = ConcurrentHashMap.newKeySet()
  private val foldRegionMarkerPolicy: MarkerPolicy = FoldRegionMarkerPolicy {
    model.setComplexDocumentChange(true)
  }
  val rootStore: SnapshotMarkerRootStore<SnapshotFoldRegion> = SnapshotMarkerRootStore.strong(
    document, SnapshotFoldRegion::class.java,
    onMarkersInvalidated = ::processInvalidatedRegions,
    onMarkersAffected = ::processAffectedRegions,
  )
  private var sizesBeforeUpdate: Map<SnapshotMarkerReference<*>, Int> = emptyMap()

  override fun createFoldRegion(
    startOffset: Int,
    endOffset: Int,
    placeholder: String,
    group: FoldingGroup?,
    neverExpands: Boolean,
  ): FoldRegionMarker {
    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    val region = SnapshotFoldRegion(
      storage = this,
      spec = spec,
      initialRange = TextRangeScalarUtil.toScalarRange(startOffset, endOffset),
      placeholder = placeholder,
      group = group,
      neverExpands = neverExpands,
    )
    register(region, startOffset, endOffset, spec, foldRegionMarkerPolicy)
    return region
  }

  override fun createCustomFoldRegion(
    startOffset: Int,
    endOffset: Int,
    renderer: CustomFoldRegionRenderer,
  ): CustomFoldRegionMarker {
    val spec = MarkerSpec.create(isGreedyToLeft = false, isGreedyToRight = false)
    val region = SnapshotCustomFoldRegion(
      storage = this,
      spec = spec,
      initialRange = TextRangeScalarUtil.toScalarRange(startOffset, endOffset),
      renderer = renderer,
    )
    register(region, startOffset, endOffset, spec, CustomFoldRegionMarkerPolicy)
    return region
  }

  override fun removeRegion(region: FoldRegionMarker): Boolean {
    if (region !is SnapshotFoldRegion || region.storage !== this) return false
    if (region.markerHandle !in registeredRegions || !region.isValid) return false
    model.notifyBeforeFoldRegionDisposed(region)
    val removed = SnapshotMarkerEngineImpl.removeRangeMarker(region)
    registeredRegions.remove(region.markerHandle)
    return removed
  }

  override fun clearRegions() {
    processAllRegions { region ->
      removeRegion(region)
      true
    }
  }

  override fun dispose() {
    rootStore.dispose(document.snapshotMarkerStores)
    registeredRegions.clear()
  }

  override fun size(): Int = registeredRegions.size

  /**
   * Processes all regions from one captured root.
   * Orders regions by start offset, length, region kind (regular precede custom), and marker ID,
   * This is the same order as [processRegionsOverlappingWith].
   *
   * Stops when [processor] returns `false`, without resolving regions in later groups.
   */
  override fun processAllRegions(processor: Processor<in FoldRegionMarker>): Boolean {
    val root = rootStore.root(currentSnapshot()) ?: return true
    var regions = arrayOfNulls<SnapshotFoldRegion>(4)
    var regionCount = 0
    var groupStart = -1

    fun processGroup(): Boolean {
      if (regionCount > 1) Arrays.sort(regions, 0, regionCount, REGION_COMPARATOR)
      for (index in 0 until regionCount) {
        if (!processor.process(regions[index]!!)) return false
      }
      regionCount = 0
      return true
    }

    val completed = root.processAllRangeMarkers { entry, ancestorDelta ->
      val start = entry.nodeStart + ancestorDelta
      if (start != groupStart) {
        if (!processGroup()) return@processAllRangeMarkers false
        groupStart = start
      }
      val region = entry.marker as? SnapshotFoldRegion
      if (region != null) {
        if (regionCount == regions.size) regions = regions.copyOf(regions.size * 2)
        regions[regionCount++] = region
      }
      true
    }
    return completed && processGroup()
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

  /**
   * Processes overlapping regions from one captured snapshot root.
   * Uses [processAllRegions] for queries that cover the whole document.
   *
   * Orders regions by start offset, length, region kind (regular precede custom), and marker ID,
   *
   * Stops when [processor] returns `false`, without resolving regions in later groups.
   * The iterator can read one entry from the next group to find the current group's boundary.
   */
  override fun processRegionsOverlappingWith(
    startOffset: Int,
    endOffset: Int,
    processor: Processor<in FoldRegionMarker>,
  ): Boolean {
    if (startOffset == 0 && endOffset >= document.textLength) {
      return processAllRegions(processor)
    }
    val root = rootStore.root(currentSnapshot()) ?: return true
    val iterator = root.overlappingIterator(startOffset, endOffset, 0)
    var entry: PMarkerRoot.MarkerEntry? = if (iterator.hasNext()) iterator.next() else return true
    val regions = ArrayList<SnapshotFoldRegion>()
    while (entry != null) {
      val regionStart = entry.nodeStart
      while (entry != null && entry.nodeStart == regionStart) {
        val region = entry.marker as? SnapshotFoldRegion
        if (region != null) regions.add(region)
        entry = if (iterator.hasNext()) iterator.next() else null
      }
      if (regions.size > 1) regions.sortWith(REGION_COMPARATOR)
      for (region in regions) {
        if (!processor.process(region)) return false
      }
      regions.clear()
    }
    return true
  }

  override fun beforeDocumentChange(event: DocumentEvent) {
    val changeStart = event.offset
    val changeEnd = changeStart + event.oldLength
    val sizes = if (event.oldLength > 0) HashMap<SnapshotMarkerReference<*>, Int>() else null
    processRegionsOverlappingWith(changeStart, changeEnd) { region ->
      region as SnapshotFoldRegion
      if (changeStart < region.endOffset && changeEnd > region.startOffset) {
        region.markDocumentRegionChanged()
      }
      if (sizes != null) {
        val size = if (region.isExpanded) 0 else region.endOffset - region.startOffset
        sizes[region.markerHandle] = size
      }
      true
    }
    sizesBeforeUpdate = sizes ?: emptyMap()
  }

  fun currentSnapshot(): DocumentSnapshot = document.core.snapshot()

  private fun register(
    region: SnapshotFoldRegion,
    startOffset: Int,
    endOffset: Int,
    spec: Byte,
    policy: MarkerPolicy,
  ) {
    check(registeredRegions.add(region.markerHandle)) { "Fold region ${region.id} is already registered" }
    rootStore.updateRoot(currentSnapshot()) {
      it.insert(region.markerHandle, startOffset, endOffset, spec, region.flavorFlags, policy = policy)
    }
  }

  private fun processAffectedRegions(handles: List<SnapshotMarkerReference<SnapshotFoldRegion>>) {
    val regions = ArrayList<SnapshotFoldRegion>(handles.size)
    @Suppress("ReplaceManualRangeWithIndicesCalls")
    for (index in 0 until handles.size) {
      val handle = handles[index]
      if (handle in registeredRegions) regions.add(handle.get())
    }
    regions.sortWith(REGION_COMPARATOR)
    removeDuplicateRegions(regions)
    for (region in regions) {
      if (region is SnapshotCustomFoldRegion && region.markerHandle in registeredRegions) {
        model.addAffectedCustomRegions(region)
      }
    }
    sizesBeforeUpdate = emptyMap()
  }

  private fun removeDuplicateRegions(regions: List<SnapshotFoldRegion>) {
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
    rootStore.updateRoot(currentSnapshot()) { it.remove(region.markerHandle) }
    if (registeredRegions.remove(region.markerHandle)) {
      region.markDisposed()
      model.snapshotFoldRegionInvalidated(region)
    }
  }

  private fun sizeBeforeUpdate(region: SnapshotFoldRegion): Int = sizesBeforeUpdate[region.markerHandle] ?: 0

  private fun processInvalidatedRegions(handles: List<SnapshotMarkerReference<SnapshotFoldRegion>>) {
    val size = handles.size
    for (index in 0 until size) {
      val handle = handles[index]
      if (registeredRegions.remove(handle)) {
        val region = handle.get()
        region.markDisposed()
        model.snapshotFoldRegionInvalidated(region)
      }
    }
  }

  companion object {
    private val REGION_COMPARATOR: Comparator<SnapshotFoldRegion> = compareBy(
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
  spec: Byte,
  initialRange: Long,
  private var placeholder: String,
  private val group: FoldingGroup?,
  private val neverExpands: Boolean,
) : SnapshotRangeMarkerImpl(
  storage.document, storage.rootStore, spec, initialRange,
), FoldRegionMarker {
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

  override fun isValidInRoot(root: PMarkerRoot): Boolean {
    val resolution = root.resolve(markerHandle)
    return PMarkerResolution.isValid(resolution) && PMarkerResolution.startOffset(resolution) != PMarkerResolution.endOffset(resolution)
  }

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

  override fun toString(): String {
    return "FoldRegion ${if (expanded) "-" else "+"}($startOffset:$endOffset)" +
           (if (isValid) "" else "(invalid)") + ", placeholder='$placeholder'"
  }

  companion object {
    private val MUTE_INNER_HIGHLIGHTERS: Key<Boolean?> = Key.create<Boolean>("mute.inner.highlighters")
    private val SHOW_GUTTER_MARK_FOR_SINGLE_LINE: Key<Boolean?> = Key.create<Boolean>("show.gutter.mark.for.single.line")

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
  spec: Byte,
  initialRange: Long,
  private val renderer: CustomFoldRegionRenderer,
) : SnapshotFoldRegion(storage, spec, initialRange, "", null, true), CustomFoldRegionMarker {
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

private class FoldRegionMarkerPolicy(
  private val onRangeAligned: () -> Unit,
) : MarkerPolicy {
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
      alignAndValidate(transformed, afterText)
    }
  }

  override fun afterRetarget(entry: PMarkerRoot.MarkerEntry, text: DocumentText): MarkerTransformResult {
    return alignAndValidate(MarkerTransformResult(entry), text)
  }

  private fun alignAndValidate(range: MarkerTransformResult, text: DocumentText): MarkerTransformResult {
    val alignedRange = alignToCharacterBoundaries(range, text)
    if (alignedRange != range) onRangeAligned()
    return if (alignedRange.startOffset < alignedRange.endOffset) {
      alignedRange
    }
    else {
      MarkerTransformResult(MarkerTransformError.EMPTY_FOLD_REGION)
    }
  }
}

private object CustomFoldRegionMarkerPolicy : MarkerPolicy {
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
      validateLineBoundaries(transformed, afterText)
    }
  }

  override fun afterRetarget(entry: PMarkerRoot.MarkerEntry, text: DocumentText): MarkerTransformResult {
    return validateLineBoundaries(MarkerTransformResult(entry), text)
  }

  private fun validateLineBoundaries(range: MarkerTransformResult, text: DocumentText): MarkerTransformResult {
    if (range.startOffset >= range.endOffset) {
      return MarkerTransformResult(MarkerTransformError.EMPTY_CUSTOM_FOLD_REGION)
    }
    val startLine = text.lineNumber(range.startOffset)
    val endLine = text.lineNumber(range.endOffset)
    return if (range.startOffset == text.lineStartOffset(startLine) && range.endOffset == text.lineEndOffset(endLine)) {
      range
    }
    else {
      MarkerTransformResult(MarkerTransformError.CUSTOM_FOLD_REGION_OUTSIDE_LINE_BOUNDARIES)
    }
  }
}

private fun alignToCharacterBoundaries(range: MarkerTransformResult, text: DocumentText): MarkerTransformResult {
  val startOffset = if (isInsideCharacterPair(range.startOffset, text)) range.startOffset - 1 else range.startOffset
  val endOffset = if (isInsideCharacterPair(range.endOffset, text)) range.endOffset - 1 else range.endOffset
  return MarkerTransformResult(startOffset, endOffset)
}

private fun isInsideCharacterPair(offset: Int, text: DocumentText): Boolean {
  if (offset <= 0 || offset >= text.length()) return false
  val chars = text.luckyChars()
  val previous = chars[offset - 1]
  return previous == '\r' && chars[offset] == '\n' || Character.isHighSurrogate(previous) && Character.isLowSurrogate(chars[offset])
}
