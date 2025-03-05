// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad

import andel.editor.RangeMarkerId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.ex.InlayModelEx
import com.intellij.openapi.editor.impl.InlayModelImpl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.Key
import com.jetbrains.rhizomedb.ChangeScope
import fleet.kernel.change
import fleet.kernel.shared
import fleet.util.UID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Experimental
import java.awt.Point
import java.awt.Rectangle
import andel.text.TextRange as AndelTextRange


@Experimental
internal class AdInlayModel private constructor(
  private val adDocument: AdDocument,
  private val mapper: AdRangeMapper<Inlay<*>>,
  private val inlayModel: InlayModelImpl,
  private val coroutineScope: CoroutineScope,
  private val repaintLambda: suspend () -> Unit,
) : InlayModelEx, Disposable {

  companion object {
    fun fromInlays(
      inlayModel: InlayModelImpl,
      adDocument: AdDocument,
      coroutineScope: CoroutineScope,
      repaintLambda: suspend () -> Unit,
    ): AdInlayModel {
      val mapper = AdRangeMapper<Inlay<*>>()
      val rangesIds = mutableListOf<RangeMarkerId>()
      val ranges = mutableListOf<AndelTextRange>()
      for (inlay in inlayModel.getInlineElementsInRange(0, Integer.MAX_VALUE)) {
        val rangeId = UID.random()
        rangesIds.add(RangeMarkerId(rangeId))
        ranges.add(AndelTextRange(inlay.offset, inlay.offset))
        mapper.register(rangeId, inlay)
      }
      adDocument.addRangeMarkers(rangesIds, ranges)
      return AdInlayModel(adDocument, mapper, inlayModel, coroutineScope, repaintLambda)
    }
  }

  init {
    val listener = object : InlayModel.Listener {
      override fun onAdded(inlay: Inlay<*>) {
        onInlayUpdate {
          // TODO: incorrect offset -> ui offset
          val rangeId = UID.random()
          adDocument.addRangeMarker(
            RangeMarkerId(rangeId),
            inlay.offset.toLong(),
            inlay.offset.toLong(),
          )
          mapper.register(rangeId, inlay)
        }
      }
      override fun onRemoved(inlay: Inlay<*>) {
        onInlayUpdate {
          val rangeId = mapper.unregister(inlay)
          if (rangeId != null) {
            adDocument.removeRangeMarker(RangeMarkerId(rangeId))
          }
        }
      }
    }
    coroutineScope.launch(Dispatchers.EDT) {
      inlayModel.addListener(listener, this@AdInlayModel)
    }
  }

  private fun onInlayUpdate(body: ChangeScope.() -> Unit) {
    coroutineScope.launch {
      change {
        shared {
          body()
        }
      }
      repaintLambda.invoke()
    }
  }

  override fun getBlockElementsForVisualLine(visualLine: Int, above: Boolean): List<Inlay<*>?> {
    return emptyList()
  }

  override fun getAfterLineEndElementsForLogicalLine(logicalLine: Int): List<Inlay<*>?> {
    return emptyList()
  }

  override fun getInlineElementsInRange(startOffset: Int, endOffset: Int): List<Inlay<*>> {
    val inlays = mutableListOf<Inlay<*>>()
    for (interval in adDocument.queryRangeMarkers(startOffset.toLong(), endOffset.toLong())) {
      val inlay = mapper.resolveRange(interval.id)
      if (inlay != null) {
        val offset = interval.from.toInt()
        inlays.add(AdInlay(offset, inlay))
      }
    }
    return inlays
  }

  override fun getAfterLineEndElementsInRange(startOffset: Int, endOffset: Int): List<Inlay<*>?> {
    return emptyList()
  }

  override fun getBlockElementsInRange(startOffset: Int, endOffset: Int): List<Inlay<*>?> {
    return emptyList()
  }

  /**
   * FoldingModel
   * CaretModel
   * EditorImpl
   * SoftWrapModel
   * EditorSizeManager
   */
  override fun addListener(listener: InlayModel.Listener, disposable: Disposable) {
  }

  override fun isInBatchMode(): Boolean {
    return false
  }

  override fun getHeightOfBlockElementsBeforeVisualLine(visualLine: Int, startOffset: Int, prevFoldRegionIndex: Int): Int {
    return inlayModel.getHeightOfBlockElementsBeforeVisualLine(visualLine, startOffset, prevFoldRegionIndex)
  }

  override fun dispose() {
  }

  // region Not yet implemented

  override fun <T : EditorCustomElementRenderer?> addInlineElement(offset: Int, relatesToPrecedingText: Boolean, renderer: T & Any): Inlay<T?>? {
    TODO("Not yet implemented")
  }

  override fun <T : EditorCustomElementRenderer?> addInlineElement(offset: Int, relatesToPrecedingText: Boolean, priority: Int, renderer: T & Any): Inlay<T?>? {
    TODO("Not yet implemented")
  }

  override fun <T : EditorCustomElementRenderer?> addInlineElement(offset: Int, properties: InlayProperties, renderer: T & Any): Inlay<T?>? {
    TODO("Not yet implemented")
  }

  override fun <T : EditorCustomElementRenderer?> addBlockElement(offset: Int, relatesToPrecedingText: Boolean, showAbove: Boolean, priority: Int, renderer: T & Any): Inlay<T?>? {
    TODO("Not yet implemented")
  }

  override fun <T : EditorCustomElementRenderer?> addBlockElement(offset: Int, properties: InlayProperties, renderer: T & Any): Inlay<T?>? {
    TODO("Not yet implemented")
  }

  override fun <T : EditorCustomElementRenderer?> addAfterLineEndElement(offset: Int, relatesToPrecedingText: Boolean, renderer: T & Any): Inlay<T?>? {
    TODO("Not yet implemented")
  }

  override fun <T : EditorCustomElementRenderer?> addAfterLineEndElement(offset: Int, properties: InlayProperties, renderer: T & Any): Inlay<T?>? {
    TODO("Not yet implemented")
  }

  override fun hasInlineElementAt(offset: Int): Boolean {
    TODO("Not yet implemented")
  }

  override fun getInlineElementAt(visualPosition: VisualPosition): Inlay<*>? {
    TODO("Not yet implemented")
  }

  override fun getElementAt(point: Point): Inlay<*>? {
    TODO("Not yet implemented")
  }

  override fun setConsiderCaretPositionOnDocumentUpdates(enabled: Boolean) {
    TODO("Not yet implemented")
  }

  override fun execute(batchMode: Boolean, operation: Runnable) {
    TODO("Not yet implemented")
  }

  // endregion
}

private class AdInlay<T : EditorCustomElementRenderer>(
  private val offset: Int,
  private val origin: Inlay<T>,
) : Inlay<T> {

  override fun getOffset(): Int {
    return offset
  }

  override fun getHeightInPixels(): Int {
    return origin.getHeightInPixels()
  }

  override fun getRenderer(): T {
    return origin.getRenderer()
  }

  override fun getWidthInPixels(): Int {
    return origin.getWidthInPixels()
  }

  override fun getPlacement(): Inlay.Placement {
    return origin.getPlacement()
  }

  override fun isRelatedToPrecedingText(): Boolean {
    return origin.isRelatedToPrecedingText()
  }

  override fun isValid(): Boolean {
    return origin.isValid
  }

  override fun <T : Any?> getUserData(key: Key<T?>): T? {
    return origin.getUserData(key)
  }

  override fun getEditor(): Editor {
    return origin.editor
  }

  // region Not yet implemented

  override fun getVisualPosition(): VisualPosition {
    TODO("Not yet implemented")
  }

  override fun getBounds(): Rectangle? {
    TODO("Not yet implemented")
  }

  override fun getGutterIconRenderer(): GutterIconRenderer? {
    TODO("Not yet implemented")
  }

  override fun update() {
    TODO("Not yet implemented")
  }

  override fun repaint() {
    TODO("Not yet implemented")
  }

  override fun dispose() {
    TODO("Not yet implemented")
  }

  override fun <T : Any?> putUserDataIfAbsent(key: Key<T?>, value: T & Any): T & Any {
    TODO("Not yet implemented")
  }

  override fun <T : Any?> replace(key: Key<T?>, oldValue: T?, newValue: T?): Boolean {
    TODO("Not yet implemented")
  }

  override fun <T : Any?> putUserData(key: Key<T?>, value: T?) {
    TODO("Not yet implemented")
  }

  // endregion
}
