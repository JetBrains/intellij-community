// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Repaints the editor when the range highlighters of its markup models change.
 * A highlighter can change on any thread.
 * The repainter joins the changes into the repaint request and applies it on the EDT.
 *
 * An added highlighter and an attributes change resolve the font families of the highlighter at once, on the thread of the change.
 * The layout and the soft wraps record the values that no source resolved, see [recordUnresolvedFontFamilies].
 * So the fonts of the families exist before the request lays out the text again,
 * see [com.intellij.openapi.editor.impl.view.EditorView.resolveFontFamilies].
 */
internal class EditorMarkupRepainter(
  private val editor: EditorImpl,
) : MarkupModelListener, PrioritizedDocumentListener {
  private val document: DocumentEx = editor.elfDocument

  /**
   * The pending repaint request, or `null` when no request is pending.
   * The first request after an applied one launches a coroutine that applies it. A later request joins the pending one.
   * A request is blocked when the editor cannot apply it, see [canApplyNow].
   * The editor applies a blocked request before the coroutine, see [applyBlockedRequest].
   *
   * Each access reads the field once, computes the result, and writes the result with one compare-and-set.
   * When another thread changed the field in the meantime, the access starts again.
   */
  private val repaintRequest = AtomicReference<RepaintRequest?>()

  private val unresolvedFontFamiliesRecords = AtomicInteger()

  override fun afterAdded(highlighter: RangeHighlighterEx) {
    val attributes = highlighter.getTextAttributes(editor.colorsScheme)
    resolveFontFamilies(attributes)
    onHighlighterChanged(highlighter, attributes, editor.canImpactGutterSize(highlighter))
  }

  override fun afterRemoved(highlighter: RangeHighlighterEx) {
    val attributes = highlighter.getTextAttributes(editor.colorsScheme)
    onHighlighterChanged(highlighter, attributes, editor.canImpactGutterSize(highlighter))
  }

  override fun attributesChanged(
    highlighter: RangeHighlighterEx,
    renderersChanged: Boolean,
    fontStyleChanged: Boolean,
    foregroundColorChanged: Boolean,
  ) {
    if (fontStyleChanged) {
      resolveFontFamilies(highlighter.getTextAttributes(editor.colorsScheme))
    }
    joinRequest(
      highlighter,
      canImpactGutterSize = renderersChanged,
      fontStyleChanged = fontStyleChanged,
      foregroundColorChanged = foregroundColorChanged,
    )
  }

  /**
   * Makes [documentChanged] of the repainter run before `documentChanged` of every other listener.
   * So at that point, the pending request has only the offsets before the change.
   * A regular listener runs after all prioritized listeners, also after the editor, which applies the pending request.
   */
  override fun getPriority(): Int {
    return Int.MIN_VALUE
  }

  /**
   * Moves the areas of the pending request with the text of the change.
   * A request that joins later in the same change already has the offsets after the change.
   */
  override fun documentChanged(event: DocumentEvent) {
    while (true) {
      val pending = repaintRequest.get() ?: return
      val moved = pending.movedBy(event)
      if (repaintRequest.compareAndSet(pending, moved)) {
        return
      }
    }
  }

  /**
   * Updates the editor after its highlighting filter starts or stops rendering the highlighter.
   */
  fun renderingChanged(highlighter: RangeHighlighterEx) {
    val attributes = highlighter.getTextAttributes(editor.colorsScheme)
    onHighlighterChanged(highlighter, attributes, canImpactGutterSize = true)
  }

  /**
   * Applies the pending request when it is blocked and [canApplyNow] is true.
   * The editor calls it at the end of a document change, a bulk update and an inlay batch.
   * A request that is not blocked waits for its coroutine, so the later changes of the same EDT event can join it.
   */
  @RequiresEdt
  fun applyBlockedRequest() {
    while (true) {
      val pending = repaintRequest.get()
      if (pending == null || !pending.blocked || !canApplyNow()) {
        return
      }
      if (repaintRequest.compareAndSet(pending, null)) {
        applyRequest(pending)
        return
      }
    }
  }

  /**
   * Applies the pending request when [canApplyNow] is true, and blocks it otherwise.
   * The coroutine that [joinRequest] launches calls it.
   */
  @RequiresEdt
  private fun applyPendingRequest() {
    while (true) {
      val pending = repaintRequest.get() ?: return
      if (canApplyNow()) {
        if (repaintRequest.compareAndSet(pending, null)) {
          applyRequest(pending)
          return
        }
      } else if (pending.blocked || repaintRequest.compareAndSet(pending, pending.asBlocked())) {
        return // the end of the batch applies the blocked request
      }
    }
  }

  /**
   * Takes the font style and the color flags from the current [attributes] of the highlighter, and gives them to [joinRequest].
   */
  private fun onHighlighterChanged(highlighter: RangeHighlighterEx, attributes: TextAttributes?, canImpactGutterSize: Boolean) {
    joinRequest(
      highlighter,
      canImpactGutterSize,
      EditorUtil.attributesImpactFontStyle(attributes),
      EditorUtil.attributesImpactForegroundColor(attributes),
    )
  }

  /**
   * Resolves the font families of the highlighters that [documentMarkup] has before the editor listens to it.
   * The editor calls it after it starts to listen.
   * So a highlighter that another thread adds meanwhile reaches the editor here or in [afterAdded].
   * The pass visits only the highlighters with forced font families, see [MarkupModelEx.overlappingFontFamiliesIterator].
   * When a value is new, it joins a request that lays out the text of these highlighters again.
   * The editor can have laid out that text already, and that layout can have recorded the value, see [recordUnresolvedFontFamilies].
   * So on the EDT it applies the pending request at once, and the new editor keeps no layout from before the resolution.
   */
  fun resolveExistingFontFamilies(documentMarkup: MarkupModelEx) {
    var values: HashSet<FontFamilies>? = null
    var fontStyleArea = RepaintArea.NONE
    documentMarkup.overlappingFontFamiliesIterator(0, documentMarkup.document.textLength).use { iterator ->
      for (highlighter in iterator) {
        val value = highlighter.forcedTextAttributes?.fontFamilies
        if (value != null) {
          if (values == null) {
            values = HashSet()
          }
          values.add(value)
          fontStyleArea = fontStyleArea.union(affectedArea(highlighter))
        }
      }
    }
    if (values != null && editor.view.resolveFontFamilies(values)) {
      joinRequest(
        fontStyleArea,
        canImpactGutterSize = false,
        fontStyleChanged = true,
        foregroundColorChanged = false,
        hasAccessibleGutterElement = false,
        fontFamilies = emptySet(),
      )
      if (EDT.isCurrentThreadEdt()) {
        applyPendingRequest()
      }
    }
  }

  /**
   * Resolves the font families of the editor highlighter, see [com.intellij.openapi.editor.highlighter.EditorHighlighter.getTextAttributesFontFamilies].
   * The editor calls it when the highlighter repaints the text from [start] to [end].
   * When a value is new, it joins a request that lays out that text again and recalculates its soft wraps.
   */
  fun resolveHighlighterFontFamilies(values: Set<FontFamilies>, start: Int, end: Int) {
    if (editor.view.resolveFontFamilies(values)) {
      joinRequest(
        RepaintArea(start, end),
        canImpactGutterSize = false,
        fontStyleChanged = true,
        foregroundColorChanged = false,
        hasAccessibleGutterElement = false,
        fontFamilies = emptySet(),
      )
    }
  }

  /**
   * Records a value that the layout or the soft wraps meet unresolved,
   * see [com.intellij.openapi.editor.impl.view.EditorFontResolver.resolvedFonts].
   * The text of [start, end) gets the editor fonts until a request resolves the value on the EDT.
   * The request then lays out that text again and recalculates its soft wraps.
   * It is safe to call on any thread, also during a layout or a paint, because the request never runs at once.
   */
  fun recordUnresolvedFontFamilies(value: FontFamilies, start: Int, end: Int) {
    unresolvedFontFamiliesRecords.incrementAndGet()
    joinRequest(
      RepaintArea(start, end),
      canImpactGutterSize = false,
      fontStyleChanged = true,
      foregroundColorChanged = false,
      hasAccessibleGutterElement = false,
      fontFamilies = setOf(value),
    )
  }

  /**
   * Returns how many times [recordUnresolvedFontFamilies] ran.
   */
  @TestOnly
  fun unresolvedFontFamiliesRecordCount(): Int {
    return unresolvedFontFamiliesRecords.get()
  }

  /**
   * Resolves the font families of [attributes], when they have them.
   * The first call for a value creates its fonts, so it runs on the thread of the change and not in the request.
   * A value stays resolved after its highlighter goes away.
   */
  private fun resolveFontFamilies(attributes: TextAttributes?) {
    val fontFamilies = attributes?.fontFamilies
    if (fontFamilies != null) {
      editor.view.resolveFontFamilies(listOf(fontFamilies))
    }
  }

  /**
   * Joins the request for the change of the highlighter into the pending request, see the other `joinRequest`.
   */
  private fun joinRequest(
    highlighter: RangeHighlighterEx,
    canImpactGutterSize: Boolean,
    fontStyleChanged: Boolean,
    foregroundColorChanged: Boolean,
  ) {
    joinRequest(
      affectedArea(highlighter),
      canImpactGutterSize,
      fontStyleChanged,
      foregroundColorChanged,
      AccessibleGutterLine.isAccessibleGutterElement(highlighter.gutterIconRenderer),
      fontFamilies = emptySet(),
    )
  }

  /**
   * Joins the request for the change of [affectedArea] into the pending request.
   * With [fontStyleChanged], the font style area is [affectedArea], and otherwise [RepaintArea.NONE].
   * The request resolves [fontFamilies] before it applies the change. A request with font families always waits for the coroutine.
   *
   * On the EDT, the editor applies the change at once when no request is pending and [canApplyNow] is true.
   * This fast path makes no [RepaintRequest] and writes nothing to [repaintRequest].
   * The coroutine applies a change from another thread, and also a change that recalculates the soft wraps.
   * So the later changes of the same EDT event join the soft wrap change, and the editor recalculates the soft wraps once.
   * The coroutine runs in any modality and takes no lock.
   * A request that the EDT stores while [canApplyNow] is false is blocked.
   */
  private fun joinRequest(
    affectedArea: RepaintArea,
    canImpactGutterSize: Boolean,
    fontStyleChanged: Boolean,
    foregroundColorChanged: Boolean,
    hasAccessibleGutterElement: Boolean,
    fontFamilies: Set<FontFamilies>,
  ) {
    val fontStyleArea = if (fontStyleChanged) {
      affectedArea
    } else {
      RepaintArea.NONE
    }
    val onEdt = EDT.isCurrentThreadEdt()
    val deferToCoroutine = !onEdt || recalculatesSoftWraps(fontStyleChanged) || fontFamilies.isNotEmpty()
    val canApply = onEdt && canApplyNow()
    var request: RepaintRequest? = null
    while (true) {
      val pending = repaintRequest.get()
      if (pending == null && !deferToCoroutine && canApply) {
        applyChange(
          affectedArea,
          fontStyleArea,
          canImpactGutterSize,
          foregroundColorChanged,
          hasAccessibleGutterElement,
        )
        return
      }
      if (request == null) {
        request = RepaintRequest(
          affectedArea,
          fontStyleArea,
          canImpactGutterSize,
          foregroundColorChanged,
          hasAccessibleGutterElement,
          fontFamilies,
          blocked = onEdt && !canApply,
        )
      }
      val joined = pending?.join(request) ?: request
      if (repaintRequest.compareAndSet(pending, joined)) {
        // A pending request already has its coroutine, and that coroutine also applies the joined request.
        // So only a request that finds no pending request launches a coroutine.
        if (pending == null) {
          editor.coroutineScope.launch(Dispatchers.UiWithModelAccess + ModalityState.any().asContextElement()) {
            applyPendingRequest()
          }
        }
        return
      }
    }
  }

  private fun affectedArea(highlighter: RangeHighlighterEx): RepaintArea {
    return RepaintArea(highlighter.affectedAreaStartOffset, highlighter.affectedAreaEndOffset)
  }

  private fun recalculatesSoftWraps(fontStyleChanged: Boolean): Boolean {
    return fontStyleChanged && editor.softWrapModel.isSoftWrappingEnabled
  }

  /**
   * Whether the editor can apply a request now.
   * It cannot during a document change, a bulk update or an inlay batch. The end of each of them calls [applyBlockedRequest].
   */
  private fun canApplyNow(): Boolean {
    return !editor.isDisposed &&
           !document.isInBulkUpdate &&
           !editor.inlayModel.isInBatchMode &&
           !editor.isDocumentChangeInProgress
  }

  @RequiresEdt
  private fun applyRequest(request: RepaintRequest) {
    if (request.fontFamilies.isNotEmpty()) {
      editor.view.resolveFontFamilies(request.fontFamilies)
    }
    applyChange(
      request.affectedArea,
      request.fontStyleArea,
      request.canImpactGutterSize,
      request.foregroundColorChanged,
      request.hasAccessibleGutterElement,
    )
  }

  /**
   * Applies the change of the highlighters to the editor.
   * The clamped areas and the repainted lines come from one [DocumentText] of the document.
   */
  @RequiresEdt
  private fun applyChange(
    affectedArea: RepaintArea,
    fontStyleArea: RepaintArea,
    canImpactGutterSize: Boolean,
    foregroundColorChanged: Boolean,
    hasAccessibleGutterElement: Boolean,
  ) {
    if (canImpactGutterSize) {
      editor.updateGutterSize()
    }
    val text = document.documentText
    val clampedAffectedArea = affectedArea.clamp(text.length())
    val clampedFontStyleArea = fontStyleArea.clamp(text.length())
    if (hasAccessibleGutterElement) {
      editor.escapeGutterAccessibleLine(clampedAffectedArea.start, clampedAffectedArea.end)
    }
    invalidateLayout(text, clampedAffectedArea, clampedFontStyleArea, foregroundColorChanged)
    editor.updateCaretCursor()
    val softWrapCountChanged = recalculateSoftWraps(clampedFontStyleArea)
    repaint(text, clampedAffectedArea, softWrapCountChanged)
  }

  /**
   * Invalidates the text layout of the affected area, and the sizes of the font style area.
   */
  private fun invalidateLayout(
    text: DocumentText,
    affectedArea: RepaintArea,
    fontStyleArea: RepaintArea,
    foregroundColorChanged: Boolean,
  ) {
    val fontStyleChanged = !fontStyleArea.isNone()
    if (affectedArea.coversText() && (fontStyleChanged || foregroundColorChanged)) {
      editor.view.invalidateRange(
        text,
        affectedArea.start,
        affectedArea.end,
        false,
      )
    }
    if (fontStyleArea.coversText()) {
      editor.view.invalidateRange(
        text,
        fontStyleArea.start,
        fontStyleArea.end,
        true,
      )
    }
  }

  /**
   * Recalculates the soft wraps of the font style area.
   *
   * @return whether the number of soft wraps changed
   */
  private fun recalculateSoftWraps(fontStyleArea: RepaintArea): Boolean {
    if (!fontStyleArea.coversText()) {
      return false
    }
    val softWrapModel = editor.softWrapModel
    val softWrapCount = softWrapModel.registeredSoftWraps.size
    softWrapModel.onFontStyleChanged(fontStyleArea.start, fontStyleArea.end)
    return softWrapModel.registeredSoftWraps.size != softWrapCount
  }

  /**
   * Repaints the lines of the affected area in [text] and one line on each side.
   * When the number of soft wraps changed, it also updates the editor size and repaints to the screen bottom.
   */
  private fun repaint(
    text: DocumentText,
    affectedArea: RepaintArea,
    softWrapCountChanged: Boolean,
  ) {
    if (editor.foldingModel.isInBatchFoldingOperation) {
      return // at the end of the batch folding operation everything is repainted
    }
    val startLineAdjusted = maxOf(0, text.lineNumber(affectedArea.start) - 1)
    val endLineAdjusted = minOf(text.lineNumber(affectedArea.end) + 1, text.lineCount())
    if (softWrapCountChanged) {
      // the visual lines below the range moved
      editor.validateSize()
      editor.repaintToScreenBottom(startLineAdjusted)
    } else {
      editor.repaintLines(startLineAdjusted, endLineAdjusted)
    }
  }

  /**
   * A request to repaint the editor for the joined changes of highlighters.
   * As `EditorImpl.repaint(startOffset, endOffset)` does, it also asks for new text layouts and sizes.
   * It can also ask for a soft wrap recalculation and a gutter size update.
   * A color change keeps the text widths, so the font style changes have their own area.
   *
   * @property affectedArea the joined affected areas of the changed highlighters
   * @property fontStyleArea the area of the font style changes, or [RepaintArea.NONE] when no font style changed
   * @property canImpactGutterSize whether a changed highlighter can change the gutter size
   * @property foregroundColorChanged whether a changed highlighter changed the color of the text
   * @property hasAccessibleGutterElement whether a changed highlighter has an accessible gutter element
   * @property fontFamilies the values that the layout or the soft wraps met unresolved. The request resolves them first.
   * @property blocked whether the editor could not apply the request, so the end of the batch applies it
   */
  private data class RepaintRequest(
    val affectedArea: RepaintArea,
    val fontStyleArea: RepaintArea,
    val canImpactGutterSize: Boolean,
    val foregroundColorChanged: Boolean,
    val hasAccessibleGutterElement: Boolean,
    val fontFamilies: Set<FontFamilies>,
    val blocked: Boolean,
  ) {
    fun movedBy(change: DocumentEvent): RepaintRequest {
      val movedAffectedArea = affectedArea.movedBy(change)
      val movedFontStyleArea = fontStyleArea.movedBy(change)
      if (affectedArea == movedAffectedArea && fontStyleArea == movedFontStyleArea) {
        return this
      }
      return copy(
        affectedArea = movedAffectedArea,
        fontStyleArea = movedFontStyleArea,
      )
    }

    fun join(other: RepaintRequest): RepaintRequest {
      return RepaintRequest(
        affectedArea.union(other.affectedArea),
        fontStyleArea.union(other.fontStyleArea),
        canImpactGutterSize || other.canImpactGutterSize,
        foregroundColorChanged || other.foregroundColorChanged,
        hasAccessibleGutterElement || other.hasAccessibleGutterElement,
        unite(fontFamilies, other.fontFamilies),
        blocked || other.blocked,
      )
    }

    fun asBlocked(): RepaintRequest {
      if (blocked) {
        return this
      }
      return copy(blocked = true)
    }
  }
}

private fun unite(first: Set<FontFamilies>, second: Set<FontFamilies>): Set<FontFamilies> {
  if (first.isEmpty()) {
    return second
  }
  if (second.isEmpty() || first.containsAll(second)) {
    return first
  }
  return first + second
}
