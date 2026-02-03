// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.markup

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.MarkupIterator
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Key
import com.intellij.util.Consumer
import com.intellij.util.Processor


internal class AdMarkupModel(private val debugName: String, private val entity: AdMarkupEntity): MarkupModelEx {

  override fun processRangeHighlightersOverlappingWith(start: Int, end: Int, processor: Processor<in RangeHighlighterEx>): Boolean {
    for (highlighter in entity.markupStorage.query(start, end)) {
      val proceed = processor.process(highlighter)
      if (!proceed) {
        break
      }
    }
    return true
  }

  // region Not yet implemented

  override fun dispose() {
    TODO("Not yet implemented")
  }

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

  override fun <T> getUserData(key: Key<T?>): T? {
    TODO("Not yet implemented")
  }

  override fun <T> putUserData(key: Key<T?>, value: T?) {
    TODO("Not yet implemented")
  }

  // endregion

  override fun toString(): String {
    return "AdMarkupModel(debugName='$debugName', entity=$entity)"
  }
}
