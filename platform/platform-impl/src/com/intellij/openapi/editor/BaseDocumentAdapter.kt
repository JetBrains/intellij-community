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

import com.intellij.openapi.util.UserDataHolderBase

abstract class BaseDocumentAdapter : UserDataHolderBase(), Document {
  override fun getModificationStamp() = 0L

  override fun insertString(offset: Int, s: CharSequence) {
    throw UnsupportedOperationException("Not implemented")
  }

  override fun deleteString(startOffset: Int, endOffset: Int) {
    throw UnsupportedOperationException("Not implemented")
  }

  override fun replaceString(startOffset: Int, endOffset: Int, s: CharSequence) {
    throw UnsupportedOperationException("Not implemented")
  }
  
  override fun setText(text: CharSequence): Unit {
    throw UnsupportedOperationException("Not implemented")
  }

  override fun createRangeMarker(startOffset: Int, endOffset: Int): RangeMarker = throw UnsupportedOperationException("Not implemented")

  override fun createRangeMarker(startOffset: Int, endOffset: Int, surviveOnExternalChange: Boolean): RangeMarker {
    throw UnsupportedOperationException("Not implemented")
  }
  
  override fun setReadOnly(isReadOnly: Boolean) {}

  override fun createGuardedBlock(startOffset: Int, endOffset: Int): RangeMarker = throw UnsupportedOperationException("Not implemented")

  override fun removeGuardedBlock(block: RangeMarker) {}

  override fun getOffsetGuard(offset: Int): RangeMarker? = null

  override fun getRangeGuard(start: Int, end: Int): RangeMarker? = null
}