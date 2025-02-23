// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.markup

import andel.intervals.Interval
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.ad.AdIntervalData
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Experimental
import java.awt.Color


@Experimental
@Serializable
data class AdRangeHighlighter(
  private val interval: AdIntervalData,
  private val data: AdRangeHighlighterData,
) : RangeHighlighterEx {

  companion object {
    fun fromInterval(interval: Interval<Long, AdRangeHighlighterData>): AdRangeHighlighter {
      return AdRangeHighlighter(AdIntervalData.fromInterval(interval), interval.data)
    }

    fun fromHighlighter(id: Long, highlighter: RangeHighlighterEx): AdRangeHighlighter? /*TODO null??*/ {
      val textAttributeKey = highlighter.textAttributesKey?.externalName
      if (textAttributeKey != null) {
        return AdRangeHighlighter(
          AdIntervalData.fromRangeMarker(id, highlighter),
          AdRangeHighlighterData(
            textAttributeKey,
            highlighter.layer,
            highlighter.targetArea == HighlighterTargetArea.EXACT_RANGE,
            highlighter.isAfterEndOfLine,
            true, // TODO: incorrect
            highlighter,
          )
        )
      }
      return null
    }
  }

  fun toInterval(): Interval<Long, AdRangeHighlighterData> {
    return interval.toInterval(data)
  }

  override fun isValid(): Boolean = true
  override fun getId(): Long = interval.id
  override fun getStartOffset(): Int = interval.start
  override fun getEndOffset(): Int = interval.end
  override fun getAffectedAreaStartOffset(): Int = interval.start // TODO(): incorrect if LINES_IN_RANGE
  override fun getAffectedAreaEndOffset(): Int = interval.end // TODO(): incorrect if LINES_IN_RANGE
  override fun isGreedyToLeft(): Boolean = interval.greedyLeft
  override fun isGreedyToRight(): Boolean = interval.greedyRight

  override fun getLayer(): Int = data.layer
  override fun getTargetArea(): HighlighterTargetArea = data.targetArea()
  override fun isAfterEndOfLine(): Boolean = data.isAfterEndOfLine
  override fun isVisibleIfFolded(): Boolean = data.isVisibleIfFolded
  override fun getCustomRenderer(): CustomHighlighterRenderer? = data.origin?.customRenderer

  override fun getTextAttributesKey(): TextAttributesKey? = data.textAttributesKey()
  override fun getTextAttributes(scheme: EditorColorsScheme?): TextAttributes? {
    val colorScheme = scheme ?: EditorColorsManager.getInstance().getGlobalScheme()
    return colorScheme.getAttributes(getTextAttributesKey())
  }

  override fun getLineSeparatorPlacement(): SeparatorPlacement? = data.origin?.lineSeparatorPlacement
  override fun getLineSeparatorRenderer(): LineSeparatorRenderer? = data.origin?.lineSeparatorRenderer
  override fun getLineSeparatorColor(): Color? = data.origin?.lineSeparatorColor
  override fun getErrorStripeTooltip(): Any? = data.origin?.errorStripeTooltip

  // region Not yet implemented

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
