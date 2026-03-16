// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.document

import andel.text.Text
import andel.text.TextView
import andel.text.charSequence
import andel.text.line
import andel.text.lineEndOffset
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.LineIterator
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.util.Processor
import org.jetbrains.annotations.NonNls


internal class AdTextDocument(val textLambda: () -> Text) : DocumentEx {

  constructor(text: Text) : this({ text })

  // region TEXT

  override fun getText(range: TextRange): String {
    return textView().string(range.startOffset, range.endOffset)
  }

  override fun getImmutableCharSequence(): CharSequence {
    return textView().charSequence()
  }

  override fun getTextLength(): Int {
    return text().charCount
  }

  // endregion


  //region LINES

  override fun getLineCount(): Int {
    val text = text()
    if (text.charCount == 0) {
      return 0
    }
    return text.lineCount.line
  }

  override fun getLineNumber(offset: Int): Int {
    return textView().lineAt(offset).line
  }

  override fun getLineStartOffset(line: Int): Int {
    return textView().lineStartOffset(line.line)
  }

  override fun getLineEndOffset(line: Int): Int {
    return textView().lineEndOffset(line.line)
  }

  override fun getLineSeparatorLength(line: Int): Int {
    // TODO: optimize
    return textView().lineEndOffset(line.line, includeLineSeparator=true) -
           textView().lineEndOffset(line.line, includeLineSeparator=false)
  }

  override fun createLineIterator(): LineIterator {
    TODO("Not yet implemented")
  }

  //endregion


  //region PRIVATE

  private fun text(): Text {
    return textLambda.invoke()
  }

  private fun textView(): TextView {
    return text().view()
  }

  //endregion


  //region UNSUPPORTED


  override fun insertString(offset: Int, s: @NonNls CharSequence) {
    throw UnsupportedOperationException()
  }

  override fun deleteString(startOffset: Int, endOffset: Int) {
    throw UnsupportedOperationException()
  }

  override fun replaceString(startOffset: Int, endOffset: Int, s: @NlsSafe CharSequence) {
    throw UnsupportedOperationException()
  }

  override fun isWritable(): Boolean {
    throw UnsupportedOperationException()
  }

  override fun getModificationStamp(): Long {
    throw UnsupportedOperationException()
  }

  override fun createRangeMarker(startOffset: Int, endOffset: Int, surviveOnExternalChange: Boolean): RangeMarker {
    throw UnsupportedOperationException()
  }

  override fun createGuardedBlock(startOffset: Int, endOffset: Int): RangeMarker {
    throw UnsupportedOperationException()
  }

  override fun setText(text: CharSequence) {
    throw UnsupportedOperationException()
  }

  override fun setModificationStamp(modificationStamp: Long) {
    throw UnsupportedOperationException()
  }

  override fun replaceText(chars: CharSequence, newModificationStamp: Long) {
    throw UnsupportedOperationException()
  }

  override fun removeRangeMarker(rangeMarker: RangeMarkerEx): Boolean {
    throw UnsupportedOperationException()
  }

  override fun registerRangeMarker(rangeMarker: RangeMarkerEx, start: Int, end: Int, greedyToLeft: Boolean, greedyToRight: Boolean, layer: Int) {
    throw UnsupportedOperationException()
  }

  override fun processRangeMarkers(processor: Processor<in RangeMarker>): Boolean {
    throw UnsupportedOperationException()
  }

  override fun processRangeMarkersOverlappingWith(start: Int, end: Int, processor: Processor<in RangeMarker>): Boolean {
    throw UnsupportedOperationException()
  }

  override fun <T> getUserData(key: Key<T?>): T? {
    throw UnsupportedOperationException()
  }

  override fun <T> putUserData(key: Key<T?>, value: T?) {
    throw UnsupportedOperationException()
  }

  //endregion
}
