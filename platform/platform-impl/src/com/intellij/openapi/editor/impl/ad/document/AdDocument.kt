// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.document

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.EditReadOnlyListener
import com.intellij.openapi.editor.ex.LineIterator
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.platform.pasta.common.DocumentEntity
import com.intellij.util.Processor
import java.beans.PropertyChangeListener
import java.util.Collections


internal class AdDocument(private val entity: DocumentEntity) : DocumentEx {

  private val textDocument = AdTextDocument {
    documentRead {
      entity.text
    }
  }


  // region TEXT

  override fun getText(range: TextRange): String {
    return textDocument.getText(range)
  }

  override fun getImmutableCharSequence(): CharSequence {
    return textDocument.immutableCharSequence
  }

  override fun getTextLength(): Int {
    return textDocument.textLength
  }

  // endregion


  //region LINES

  override fun getLineCount(): Int {
    return textDocument.lineCount
  }

  override fun getLineNumber(offset: Int): Int {
    return textDocument.getLineNumber(offset)
  }

  override fun getLineStartOffset(line: Int): Int {
    return textDocument.getLineStartOffset(line)
  }

  override fun getLineEndOffset(line: Int): Int {
    return textDocument.getLineEndOffset(line)
  }

  override fun getLineSeparatorLength(line: Int): Int {
    return textDocument.getLineSeparatorLength(line)
  }

  override fun createLineIterator(): LineIterator {
    return textDocument.createLineIterator()
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

  @Suppress("OVERRIDE_DEPRECATION")
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

  override fun <T> getUserData(key: Key<T>): T? {
    TODO("Not yet implemented")
  }

  override fun <T> putUserData(key: Key<T>, value: T?) {
    TODO("Not yet implemented")
  }

  //endregion


  //region PRIVATE


  private fun <T> documentRead(block: () -> T): T {
    return block()
  }

  //endregion
}
