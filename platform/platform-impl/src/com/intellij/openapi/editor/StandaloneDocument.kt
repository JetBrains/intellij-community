/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.EditReadOnlyListener
import com.intellij.openapi.editor.ex.LineIterator
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.Processor
import java.beans.PropertyChangeListener

abstract class StandaloneDocumentEx : StandaloneDocument(), DocumentEx {
  override fun registerRangeMarker(rangeMarker: RangeMarkerEx,
                                     start: Int,
                                     end: Int,
                                     greedyToLeft: Boolean,
                                     greedyToRight: Boolean,
                                     layer: Int) {}

  override fun processRangeMarkers(processor: Processor<in RangeMarker>) = false

  override fun processRangeMarkersOverlappingWith(start: Int, end: Int, processor: Processor<in RangeMarker>) = false

  override fun removeRangeMarker(rangeMarker: RangeMarkerEx) = false

  override fun setStripTrailingSpacesEnabled(isEnabled: Boolean) {}

  override fun setModificationStamp(modificationStamp: Long) {}

  override fun addEditReadOnlyListener(listener: EditReadOnlyListener) {}

  override fun removeEditReadOnlyListener(listener: EditReadOnlyListener) {}

  override fun replaceText(chars: CharSequence, newModificationStamp: Long) {}

  override fun moveText(srcStart: Int, srcEnd: Int, dstOffset: Int) {}

  override fun suppressGuardedExceptions() {}

  override fun unSuppressGuardedExceptions() {}

  override fun isInEventsHandling() = false

  override fun isInBulkUpdate() = false

  override fun setInBulkUpdate(value: Boolean) {}

  override final fun getModificationSequence() = 0

  override fun createLineIterator(): LineIterator = throw UnsupportedOperationException()
}

abstract class StandaloneDocument : UserDataHolderBase(), Document {
  override fun getLineSeparatorLength(line: Int) = 0

  override fun getModificationStamp() = 0L

  override fun insertString(offset: Int, s: CharSequence) {}

  override fun deleteString(startOffset: Int, endOffset: Int) {}

  override fun replaceString(startOffset: Int, endOffset: Int, s: CharSequence) {
  }

  override fun isWritable() = false

  override fun fireReadOnlyModificationAttempt() {
  }

  override fun addDocumentListener(listener: DocumentListener) {
  }

  override fun addDocumentListener(listener: DocumentListener, parentDisposable: Disposable) {
  }

  override fun removeDocumentListener(listener: DocumentListener) {
  }

  override fun createRangeMarker(startOffset: Int, endOffset: Int): RangeMarker = throw UnsupportedOperationException("Not implemented")

  override fun createRangeMarker(startOffset: Int,
                                 endOffset: Int,
                                 surviveOnExternalChange: Boolean): RangeMarker = throw UnsupportedOperationException("Not implemented")

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
  }

  override fun setReadOnly(isReadOnly: Boolean) {}

  override fun createGuardedBlock(startOffset: Int, endOffset: Int): RangeMarker = throw UnsupportedOperationException("Not implemented")

  override fun removeGuardedBlock(block: RangeMarker) {}

  override fun getOffsetGuard(offset: Int): RangeMarker? = null

  override fun getRangeGuard(start: Int, end: Int): RangeMarker? = null

  override final fun setCyclicBufferSize(bufferSize: Int) {
  }

  override fun setText(text: CharSequence): Unit = throw UnsupportedOperationException("Not implemented")

  override fun createRangeMarker(textRange: TextRange): RangeMarker = throw UnsupportedOperationException("Not implemented")
}