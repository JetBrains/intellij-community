// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad

import andel.editor.RangeMarkerId
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.MarkupIterator
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.util.Consumer
import com.intellij.util.Processor
import com.jetbrains.rhizomedb.ChangeScope
import fleet.kernel.change
import fleet.kernel.shared
import fleet.util.UID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import andel.text.TextRange as AndelTextRange


@ApiStatus.Experimental
internal class AdMarkupModel private constructor(
  private val adDocument: AdDocument,
  private val mapper: AdRangeMapper<RangeHighlighterEx>,
  markupModel: MarkupModelEx,
  private val coroutineScope: CoroutineScope,
  private val repaintLambda: suspend () -> Unit,
) : MarkupModelEx, Disposable {

  companion object {
    fun fromMarkup(
      markupModel: MarkupModelEx,
      adDocument: AdDocument,
      coroutineScope: CoroutineScope,
      repaintLambda: suspend () -> Unit,
    ): AdMarkupModel {
      val mapper = addExistingHighlighters(markupModel, adDocument)
      val adMarkupModel = AdMarkupModel(adDocument, mapper, markupModel, coroutineScope, repaintLambda)
      return adMarkupModel
    }

    private fun addExistingHighlighters(markupModel: MarkupModelEx, adDocument: AdDocument): AdRangeMapper<RangeHighlighterEx> {
      // TODO: there is a gap between allHighlighters and addMarkupModelListener, a highlighter may be missed
      val mapper = AdRangeMapper<RangeHighlighterEx>()
      val rangesIds = mutableListOf<RangeMarkerId>()
      val ranges = mutableListOf<AndelTextRange>()
      for (highlighter in markupModel.allHighlighters) {
        if (highlighter.isValid) {
          val rangeId = UID.random()
          rangesIds.add(RangeMarkerId(rangeId))
          ranges.add(AndelTextRange(highlighter.startOffset, highlighter.endOffset))
          mapper.register(rangeId, highlighter as RangeHighlighterEx)
        }
      }
      adDocument.addRangeMarkers(rangesIds, ranges)
      return mapper
    }
  }

  init {
    markupModel.addMarkupModelListener(this, object : MarkupModelListener {
      override fun afterAdded(highlighter: RangeHighlighterEx) {
        onHighlightUpdate {
          // TODO: incorrect offset -> ui offset
          val rangeId = UID.random()
          adDocument.addRangeMarker(
            RangeMarkerId(rangeId),
            highlighter.startOffset.toLong(),
            highlighter.endOffset.toLong(),
          )
          mapper.register(rangeId, highlighter)
        }
      }
      override fun afterRemoved(highlighter: RangeHighlighterEx) {
        onHighlightUpdate {
          val rangeId = mapper.unregister(highlighter)
          if (rangeId != null) {
            adDocument.removeRangeMarker(RangeMarkerId(rangeId))
          }
        }
      }
    })
  }

  override fun processRangeHighlightersOverlappingWith(
    start: Int,
    end: Int,
    processor: Processor<in RangeHighlighterEx>,
  ): Boolean {
    for (interval in adDocument.queryRangeMarkers(start.toLong(), end.toLong())) {
      val rh = mapper.resolveRange(interval.id)
      if (rh != null && rh.isValid) {
        val startOffset = interval.from.toInt()
        val endOffset = interval.to.toInt()
        val proceed = processor.process(AdRangeHighlighter(
          startOffset,
          endOffset,
          affectedStartOffset(startOffset, rh.targetArea),
          affectedEndOffset(endOffset, rh.targetArea),
          rh
        ))
        if (!proceed) {
          break
        }
      }
    }
    return true
  }

  override fun dispose() {
  }

  private fun onHighlightUpdate(body: ChangeScope.() -> Unit) {
    coroutineScope.launch {
      change {
        shared {
          body()
        }
      }
      repaintLambda.invoke()
    }
  }

  private fun affectedStartOffset(startOffset: Int, area: HighlighterTargetArea): Int {
    if (area == HighlighterTargetArea.EXACT_RANGE) {
      return startOffset
    }
    val line = adDocument.getLineNumber(startOffset)
    return adDocument.getLineStartOffset(line)
  }

  private fun affectedEndOffset(endOffset: Int, area: HighlighterTargetArea): Int {
    if (area == HighlighterTargetArea.EXACT_RANGE) {
      return endOffset
    }
    val line = adDocument.getLineNumber(endOffset)
    return adDocument.getLineEndOffset(line)
  }

  // region Not yet implemented

  override fun addPersistentLineHighlighter(textAttributesKey: TextAttributesKey?, lineNumber: Int, layer: Int): RangeHighlighterEx? {
    TODO("Not yet implemented")
  }

  override fun addPersistentLineHighlighter(lineNumber: Int, layer: Int, textAttributes: TextAttributes?): RangeHighlighterEx? {
    TODO("Not yet implemented")
  }

  override fun containsHighlighter(highlighter: RangeHighlighter): Boolean {
    TODO("Not yet implemented")
  }

  override fun addMarkupModelListener(parentDisposable: Disposable, listener: MarkupModelListener) {
    TODO("Not yet implemented")
  }

  override fun setRangeHighlighterAttributes(highlighter: RangeHighlighter, textAttributes: TextAttributes) {
    TODO("Not yet implemented")
  }

  override fun processRangeHighlightersOutside(start: Int, end: Int, processor: Processor<in RangeHighlighterEx>): Boolean {
    TODO("Not yet implemented")
  }

  override fun overlappingIterator(startOffset: Int, endOffset: Int): MarkupIterator<RangeHighlighterEx?> {
    TODO("Not yet implemented")
  }

  override fun addRangeHighlighterAndChangeAttributes(textAttributesKey: TextAttributesKey?, startOffset: Int, endOffset: Int, layer: Int, targetArea: HighlighterTargetArea, isPersistent: Boolean, changeAttributesAction: Consumer<in RangeHighlighterEx>?): RangeHighlighterEx {
    TODO("Not yet implemented")
  }

  override fun changeAttributesInBatch(highlighter: RangeHighlighterEx, changeAttributesAction: Consumer<in RangeHighlighterEx>) {
    TODO("Not yet implemented")
  }

  override fun getDocument(): Document {
    TODO("Not yet implemented")
  }

  override fun addRangeHighlighter(textAttributesKey: TextAttributesKey?, startOffset: Int, endOffset: Int, layer: Int, targetArea: HighlighterTargetArea): RangeHighlighter {
    TODO("Not yet implemented")
  }

  override fun addRangeHighlighter(startOffset: Int, endOffset: Int, layer: Int, textAttributes: TextAttributes?, targetArea: HighlighterTargetArea): RangeHighlighter {
    TODO("Not yet implemented")
  }

  override fun addLineHighlighter(textAttributesKey: TextAttributesKey?, line: Int, layer: Int): RangeHighlighter {
    TODO("Not yet implemented")
  }

  override fun addLineHighlighter(line: Int, layer: Int, textAttributes: TextAttributes?): RangeHighlighter {
    TODO("Not yet implemented")
  }

  override fun removeHighlighter(rangeHighlighter: RangeHighlighter) {
    TODO("Not yet implemented")
  }

  override fun removeAllHighlighters() {
    TODO("Not yet implemented")
  }

  override fun getAllHighlighters(): Array<out RangeHighlighter> {
    TODO("Not yet implemented")
  }

  override fun <T : Any?> getUserData(key: Key<T?>): T? {
    TODO("Not yet implemented")
  }

  override fun <T : Any?> putUserData(key: Key<T?>, value: T?) {
    TODO("Not yet implemented")
  }

  // endregion
}

private class AdRangeHighlighter(
  private val startOffset: Int,
  private val endOffset: Int,
  private val startAffectedOffset: Int,
  private val endAffectedOffset: Int,
  private val origin: RangeHighlighterEx,
) : RangeHighlighterEx by origin {

  override fun isValid(): Boolean = true
  override fun getStartOffset(): Int = startOffset
  override fun getEndOffset(): Int = endOffset
  override fun getAffectedAreaStartOffset(): Int = startAffectedOffset
  override fun getAffectedAreaEndOffset(): Int = endAffectedOffset

  override fun getCustomRenderer(): CustomHighlighterRenderer? = origin.customRenderer // TODO
  override fun getLineSeparatorPlacement(): SeparatorPlacement? = origin.lineSeparatorPlacement // TODO
  override fun getLineSeparatorRenderer(): LineSeparatorRenderer? = origin.lineSeparatorRenderer // TODO

  override fun getLineSeparatorColor(): Color? = origin.lineSeparatorColor
  override fun isAfterEndOfLine(): Boolean = origin.isAfterEndOfLine
  override fun getTextAttributes(scheme: EditorColorsScheme?): TextAttributes? = origin.getTextAttributes(scheme)
  override fun isVisibleIfFolded(): Boolean = origin.isVisibleIfFolded
  override fun getLayer(): Int = origin.layer
  override fun getTargetArea(): HighlighterTargetArea = origin.targetArea
  override fun getErrorStripeTooltip(): Any? = origin.errorStripeTooltip

  // region Not yet implemented

  override fun getId(): Long {
    TODO("Not yet implemented")
  }

  override fun getTextRange(): TextRange {
    TODO("Not yet implemented")
  }

  override fun getForcedTextAttributes(): TextAttributes? {
    TODO("Not yet implemented")
  }

  override fun getForcedErrorStripeMarkColor(): Color? {
    TODO("Not yet implemented")
  }

  override fun setAfterEndOfLine(value: Boolean) {
    TODO("Not yet implemented")
  }

  override fun fireChanged(renderersChanged: Boolean, fontStyleChanged: Boolean, foregroundColorChanged: Boolean) {
    TODO("Not yet implemented")
  }

  override fun setTextAttributes(textAttributes: TextAttributes?) {
    TODO("Not yet implemented")
  }

  override fun setVisibleIfFolded(value: Boolean) {
    TODO("Not yet implemented")
  }

  override fun isPersistent(): Boolean {
    TODO("Not yet implemented")
  }

  override fun isRenderedInGutter(): Boolean {
    TODO("Not yet implemented")
  }

  override fun copyFrom(other: RangeHighlighterEx) {
    TODO("Not yet implemented")
  }

  override fun getTextAttributesKey(): TextAttributesKey? {
    TODO("Not yet implemented")
  }

  override fun setTextAttributesKey(textAttributesKey: TextAttributesKey) {
    TODO("Not yet implemented")
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun getTextAttributes(): TextAttributes? {
    TODO("Not yet implemented")
  }

  override fun getLineMarkerRenderer(): LineMarkerRenderer? {
    TODO("Not yet implemented")
  }

  override fun setLineMarkerRenderer(renderer: LineMarkerRenderer?) {
    TODO("Not yet implemented")
  }

  override fun setCustomRenderer(renderer: CustomHighlighterRenderer?) {
    TODO("Not yet implemented")
  }

  override fun getGutterIconRenderer(): GutterIconRenderer? {
    TODO("Not yet implemented")
  }

  override fun setGutterIconRenderer(renderer: GutterIconRenderer?) {
    TODO("Not yet implemented")
  }

  override fun getErrorStripeMarkColor(scheme: EditorColorsScheme?): Color? {
    TODO("Not yet implemented")
  }

  override fun setErrorStripeMarkColor(color: Color?) {
    TODO("Not yet implemented")
  }

  override fun setErrorStripeTooltip(tooltipObject: @NlsContexts.Tooltip Any?) {
    TODO("Not yet implemented")
  }

  override fun isThinErrorStripeMark(): Boolean {
    TODO("Not yet implemented")
  }

  override fun setThinErrorStripeMark(value: Boolean) {
    TODO("Not yet implemented")
  }

  override fun setLineSeparatorColor(color: Color?) {
    TODO("Not yet implemented")
  }

  override fun setLineSeparatorRenderer(renderer: LineSeparatorRenderer?) {
    TODO("Not yet implemented")
  }

  override fun setLineSeparatorPlacement(placement: SeparatorPlacement?) {
    TODO("Not yet implemented")
  }

  override fun setEditorFilter(filter: MarkupEditorFilter) {
    TODO("Not yet implemented")
  }

  override fun getEditorFilter(): MarkupEditorFilter {
    TODO("Not yet implemented")
  }

  override fun getDocument(): Document {
    TODO("Not yet implemented")
  }

  override fun setGreedyToLeft(greedy: Boolean) {
    TODO("Not yet implemented")
  }

  override fun setGreedyToRight(greedy: Boolean) {
    TODO("Not yet implemented")
  }

  override fun isGreedyToRight(): Boolean {
    TODO("Not yet implemented")
  }

  override fun isGreedyToLeft(): Boolean {
    TODO("Not yet implemented")
  }

  override fun dispose() {
    TODO("Not yet implemented")
  }

  override fun <T : Any?> getUserData(key: Key<T?>): T? {
    TODO("Not yet implemented")
  }

  override fun <T : Any?> putUserData(key: Key<T?>, value: T?) {
    TODO("Not yet implemented")
  }

  // endregion
}
