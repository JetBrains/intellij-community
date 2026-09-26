// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.customwrap

import com.intellij.openapi.editor.CustomWrap
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.impl.CustomWrapModelImpl
import com.intellij.openapi.editor.impl.RangeMarkerImpl
import com.intellij.openapi.editor.impl.isValidCustomWrapOffset

internal class CustomWrapImpl(
  offset: Int,
  document: DocumentEx,
  val model: CustomWrapModelImpl,
  override val indent: Int,
  override val priority: Int,
) : RangeMarkerImpl(document, offset, offset, false, true),
    CustomWrap {

  init {
    require(indent >= 0) { "Indent must be non-negative, got: $indent" }
  }

  override val offset: Int
    get() = this.startOffset

  override fun toString(): String = "CustomWrapImpl[offset=$offset, indent=$indent]"

  override fun dispose() {
    super.dispose()
  }

  override fun onReTarget(e: DocumentEvent) {
    if (!isValidCustomWrapOffset(intervalStart(), document)) {
      invalidate()
    }
  }

  override fun changedUpdateImpl(e: DocumentEvent) {
    super.changedUpdateImpl(e)
    if (isValid && !isValidCustomWrapOffset(intervalStart(), document)) {
      invalidate()
    }
  }
}
