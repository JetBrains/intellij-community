// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Repaints the editor when the range highlighters of its markup models change.
 * A highlighter can change on any thread.
 * The repainter joins the changes into the repaint request and applies it on the EDT.
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

  override fun afterAdded(highlighter: RangeHighlighterEx) {
    onHighlighterChanged(highlighter, editor.canImpactGutterSize(highlighter))
  }

  override fun afterRemoved(highlighter: RangeHighlighterEx) {
    onHighlighterChanged(highlighter, editor.canImpactGutterSize(highlighter))
  }

  override fun attributesChanged(
    highlighter: RangeHighlighterEx,
    renderersChanged: Boolean,
    fontStyleChanged: Boolean,
    foregroundColorChanged: Boolean,
  ) {
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
    onHighlighterChanged(highlighter, canImpactGutterSize = true)
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
   * Takes the font style and the color flags from the current attributes of the highlighter, and gives them to [joinRequest].
   */
  private fun onHighlighterChanged(highlighter: RangeHighlighterEx, canImpactGutterSize: Boolean) {
    val attributes = highlighter.getTextAttributes(editor.colorsScheme)
    joinRequest(
      highlighter,
      canImpactGutterSize,
      EditorUtil.attributesImpactFontStyle(attributes),
      EditorUtil.attributesImpactForegroundColor(attributes),
    )
  }

  /**
   * Joins the request for the change of the highlighter into the pending request.
   *
   * On the EDT, the editor applies the change at once when no request is pending and [canApplyNow] is true.
   * This fast path makes no [RepaintRequest] and writes nothing to [repaintRequest].
   * The coroutine applies a change from another thread, and also a change that recalculates the soft wraps.
   * So the later changes of the same EDT event join the soft wrap change, and the editor recalculates the soft wraps once.
   * The coroutine runs in any modality and takes no lock.
   * A request that the EDT stores while [canApplyNow] is false is blocked.
   */
  private fun joinRequest(
    highlighter: RangeHighlighterEx,
    canImpactGutterSize: Boolean,
    fontStyleChanged: Boolean,
    foregroundColorChanged: Boolean,
  ) {
    val affectedArea = RepaintArea(highlighter.affectedAreaStartOffset, highlighter.affectedAreaEndOffset)
    val fontStyleArea = if (fontStyleChanged) {
      affectedArea
    } else {
      RepaintArea.NONE
    }
    val hasAccessibleGutterElement = AccessibleGutterLine.isAccessibleGutterElement(highlighter.gutterIconRenderer)
    val onEdt = EDT.isCurrentThreadEdt()
    val deferToCoroutine = !onEdt || recalculatesSoftWraps(fontStyleChanged)
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
   * @property blocked whether the editor could not apply the request, so the end of the batch applies it
   */
  private data class RepaintRequest(
    val affectedArea: RepaintArea,
    val fontStyleArea: RepaintArea,
    val canImpactGutterSize: Boolean,
    val foregroundColorChanged: Boolean,
    val hasAccessibleGutterElement: Boolean,
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
