// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.v2

import andel.text.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.EditReadOnlyListener
import com.intellij.openapi.editor.ex.LineIterator
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.platform.pasta.common.DocumentEntity
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus.Experimental
import java.beans.PropertyChangeListener
import java.util.*

@Experimental
internal class AdDocumentV2(private val entity: DocumentEntity) : DocumentEx {

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


  //region MUTATE TEXT

  override fun insertString(offset: Int, chars: CharSequence) {
    TODO("Not yet implemented")
  }

  override fun deleteString(startOffset: Int, endOffset: Int) {
    TODO("Not yet implemented")
  }

  override fun replaceString(startOffset: Int, endOffset: Int, chars: CharSequence) {
    TODO("Not yet implemented")
  }

  override fun setText(chars: CharSequence) {
    TODO("Not yet implemented")
  }

  override fun replaceText(chars: CharSequence, newModificationStamp: Long) {
    TODO("Not yet implemented")
  }

  //endregion


  //region RANGE MARKERS

  override fun createRangeMarker(startOffset: Int, endOffset: Int, surviveOnExternalChange: Boolean): RangeMarkerEx {
    TODO("Not yet implemented")
  }

  override fun registerRangeMarker(rangeMarker: RangeMarkerEx, start: Int, end: Int, greedyToLeft: Boolean, greedyToRight: Boolean, layer: Int) {
    TODO("Not yet implemented")
  }

  override fun removeRangeMarker(rangeMarker: RangeMarkerEx): Boolean {
    TODO("Not yet implemented")
  }

  override fun processRangeMarkers(processor: Processor<in RangeMarker>): Boolean {
    TODO("Not yet implemented")
  }

  override fun processRangeMarkersOverlappingWith(start: Int, end: Int, processor: Processor<in RangeMarker>): Boolean {
    TODO("Not yet implemented")
  }

  //endregion


  //region DOC LISTENERS

  override fun addDocumentListener(listener: DocumentListener, parentDisposable: Disposable) {
    //TODO("Not yet implemented")
  }

  override fun addDocumentListener(listener: DocumentListener) {
    //TODO("Not yet implemented")
  }

  override fun removeDocumentListener(listener: DocumentListener) {
    //TODO("Not yet implemented")
  }

  //endregion


  //region PROP LISTENER

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    TODO("Not yet implemented")
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    TODO("Not yet implemented")
  }

  //endregion


  //region TRACK MODIFICATIONS

  override fun isLineModified(line: Int): Boolean {
    TODO("Not yet implemented")
  }

  override fun getModificationStamp(): Long {
    return documentRead {
      entity.editLog.timestamp
    }
  }

  override fun setModificationStamp(modificationStamp: Long) {
    TODO("Not yet implemented")
  }

  override fun clearLineModificationFlags() {
    TODO("Not yet implemented")
  }

  override fun getModificationSequence(): Int {
    TODO("Not yet implemented")
  }

  //endregion


  //region GUARDED BLOCKS

  override fun createGuardedBlock(startOffset: Int, endOffset: Int): RangeMarker {
    TODO("Not yet implemented")
  }

  override fun removeGuardedBlock(block: RangeMarker) {
    TODO("Not yet implemented")
  }

  override fun getOffsetGuard(offset: Int): RangeMarker? {
    TODO("Not yet implemented")
  }

  override fun getRangeGuard(start: Int, end: Int): RangeMarker? {
    TODO("Not yet implemented")
  }

  override fun getGuardedBlocks(): List<RangeMarker> {
    // TODO("Not yet implemented")
    return Collections.emptyList()
  }

  override fun startGuardedBlockChecking() {
    TODO("Not yet implemented")
  }

  override fun stopGuardedBlockChecking() {
    TODO("Not yet implemented")
  }

  override fun suppressGuardedExceptions() {
    TODO("Not yet implemented")
  }

  override fun unSuppressGuardedExceptions() {
    TODO("Not yet implemented")
  }

  //endregion


  //region BULK MODE

  override fun isInBulkUpdate(): Boolean {
    return false // TODO("Not yet implemented")
  }

  @Deprecated("Deprecated in Java")
  override fun setInBulkUpdate(value: Boolean) {
    TODO("Not yet implemented")
  }

  override fun isInEventsHandling(): Boolean {
    TODO("Not yet implemented")
  }

  //endregion


  //region READ-ONLY MODE

  override fun setReadOnly(isReadOnly: Boolean) {
    TODO("Not yet implemented")
  }

  override fun isWritable(): Boolean {
    TODO("Not yet implemented")
  }

  //endregion


  //region SPECIAL MODES

  override fun setCyclicBufferSize(bufferSize: Int) {
    TODO("Not yet implemented")
  }

  override fun setStripTrailingSpacesEnabled(isEnabled: Boolean) {
    TODO("Not yet implemented")
  }

  //endregion


  //region READ ATTEMPT

  override fun fireReadOnlyModificationAttempt() {
    TODO("Not yet implemented")
  }

  override fun addEditReadOnlyListener(listener: EditReadOnlyListener) {
    TODO("Not yet implemented")
  }

  override fun removeEditReadOnlyListener(listener: EditReadOnlyListener) {
    TODO("Not yet implemented")
  }

  //endregion


  //region USER DATA

  override fun <T : Any?> getUserData(key: Key<T>): T? {
    TODO("Not yet implemented")
  }

  override fun <T : Any?> putUserData(key: Key<T>, value: T?) {
    TODO("Not yet implemented")
  }

  //endregion


  //region PRIVATE

  private fun text(): Text {
    return documentRead {
      entity.text
    }
  }

  private fun textView(): TextView {
    return text().view()
  }

  private fun <T> documentRead(block: () -> T): T {
    return block()
  }

  //endregion
}
