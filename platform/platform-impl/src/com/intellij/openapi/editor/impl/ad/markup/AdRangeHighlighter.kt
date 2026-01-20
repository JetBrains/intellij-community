// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.markup

import andel.intervals.Interval
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.ad.document.AdTextDocument
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.awt.Color
import java.lang.ref.WeakReference
import kotlin.math.min


@Serializable
internal data class AdRangeHighlighter(
  private val interval: AdIntervalData,
  private val data: AdRangeHighlighterData,
  @Transient private val document: AdTextDocument? = null,
) : RangeHighlighterEx {

  companion object {
    fun fromInterval(document: AdTextDocument, interval: Interval<Long, AdRangeHighlighterData>): AdRangeHighlighter {
      return AdRangeHighlighter(AdIntervalData.fromInterval(interval), interval.data, document)
    }

    fun fromHighlighter(id: Long, highlighter: RangeHighlighterEx): AdRangeHighlighter /*TODO null??*/ {
      return AdRangeHighlighter(
        interval = AdIntervalData.fromRangeMarker(id, highlighter),
        data = AdRangeHighlighterData(
          highlighter.id,
          highlighter.textAttributesKey?.externalName,
          highlighter.layer,
          highlighter.targetArea == HighlighterTargetArea.EXACT_RANGE,
          highlighter.isAfterEndOfLine,
          highlighter.isVisibleIfFolded,
          highlighter.isThinErrorStripeMark,
          highlighter.isPersistent,
          WeakReference(highlighter),
        ),
      )
    }
  }

  fun toInterval(): Interval<Long, AdRangeHighlighterData> {
    return interval.toInterval(data)
  }

  override fun isValid(): Boolean = true
  override fun getId(): Long = interval.id
  override fun getStartOffset(): Int = interval.start
  override fun getEndOffset(): Int = interval.end
  override fun getAffectedAreaStartOffset(): Int = getAffectedStartOffset()
  override fun getAffectedAreaEndOffset(): Int = getAffectedEndOffset()
  override fun isGreedyToLeft(): Boolean = interval.greedyLeft
  override fun isGreedyToRight(): Boolean = interval.greedyRight

  override fun getDocument(): Document = document!! // doc is null only during serde

  override fun getLayer(): Int = data.layer
  override fun getTargetArea(): HighlighterTargetArea = data.targetArea()
  override fun isAfterEndOfLine(): Boolean = data.isAfterEndOfLine
  override fun isVisibleIfFolded(): Boolean = data.isVisibleIfFolded
  override fun isThinErrorStripeMark(): Boolean = data.isThinErrorStripeMark
  override fun isPersistent(): Boolean = data.isPersistent
  override fun getCustomRenderer(): CustomHighlighterRenderer? = data.origin()?.customRenderer

  override fun getTextAttributesKey(): TextAttributesKey? = data.textAttributesKey()
  override fun getTextAttributes(scheme: EditorColorsScheme?): TextAttributes? = getTextAttr(scheme)

  override fun getLineSeparatorPlacement(): SeparatorPlacement? = data.origin()?.lineSeparatorPlacement
  override fun getLineSeparatorRenderer(): LineSeparatorRenderer? = data.origin()?.lineSeparatorRenderer
  override fun getLineSeparatorColor(): Color? = data.origin()?.lineSeparatorColor
  override fun getErrorStripeTooltip(): Any? = data.origin()?.errorStripeTooltip
  override fun getLineMarkerRenderer(): LineMarkerRenderer? = data.origin()?.lineMarkerRenderer
  override fun getGutterIconRenderer(): GutterIconRenderer? = data.origin()?.gutterIconRenderer
  override fun getErrorStripeMarkColor(scheme: EditorColorsScheme?): Color? = data.origin()?.getErrorStripeMarkColor(scheme)
  override fun getForcedTextAttributes(): TextAttributes? = data.origin()?.forcedTextAttributes
  override fun getForcedErrorStripeMarkColor(): Color? = data.origin()?.forcedErrorStripeMarkColor
  override fun getEditorFilter(): MarkupEditorFilter = data.origin()?.editorFilter ?: MarkupEditorFilter.EMPTY
  override fun <T> getUserData(key: Key<T?>): T? = data.origin()?.getUserData(key)

  // region Not yet implemented

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

  override fun copyFrom(other: RangeHighlighterEx) {
    TODO("Not yet implemented")
  }

  override fun setTextAttributesKey(textAttributesKey: TextAttributesKey) {
    TODO("Not yet implemented")
  }

  override fun setLineMarkerRenderer(renderer: LineMarkerRenderer?) {
    TODO("Not yet implemented")
  }

  override fun setCustomRenderer(renderer: CustomHighlighterRenderer?) {
    TODO("Not yet implemented")
  }

  override fun setGutterIconRenderer(renderer: GutterIconRenderer?) {
    TODO("Not yet implemented")
  }

  override fun setErrorStripeMarkColor(color: Color?) {
    TODO("Not yet implemented")
  }

  override fun setErrorStripeTooltip(tooltipObject: @NlsContexts.Tooltip Any?) {
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

  override fun setGreedyToLeft(greedy: Boolean) {
    TODO("Not yet implemented")
  }

  override fun setGreedyToRight(greedy: Boolean) {
    TODO("Not yet implemented")
  }

  override fun dispose() {
    TODO("Not yet implemented")
  }

  override fun <T> putUserData(key: Key<T?>, value: T?) {
    TODO("Not yet implemented")
  }

  // endregion

  private fun getTextAttr(scheme: EditorColorsScheme?): TextAttributes? {
    val forced = getForcedTextAttributes()
    if (forced != null) {
      return forced
    }
    val key = getTextAttributesKey()
    if (key == null) {
      return null
    }
    val colorScheme = scheme ?: EditorColorsManager.getInstance().getGlobalScheme()
    return colorScheme.getAttributes(key)
  }

  private fun getAffectedStartOffset(): Int {
    val offset = startOffset
    if (targetArea == HighlighterTargetArea.EXACT_RANGE) {
      return offset
    }
    val document = getDocument()
    val textLength = document.textLength
    return if (offset >= textLength) {
      offset
    } else {
      val line = document.getLineNumber(offset)
      document.getLineStartOffset(line)
    }
  }

  private fun getAffectedEndOffset(): Int {
    val offset = endOffset
    if (targetArea == HighlighterTargetArea.EXACT_RANGE) {
      return offset
    }
    val document = getDocument()
    val textLength = document.textLength
    return if (offset >= textLength) {
      offset
    } else {
      val line = document.getLineNumber(offset)
      min(textLength, 1 + document.getLineEndOffset(line))
    }
  }
}
