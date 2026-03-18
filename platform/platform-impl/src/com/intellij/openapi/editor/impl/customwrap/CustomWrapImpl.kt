// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.customwrap

import com.intellij.openapi.editor.CustomWrap
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.RangeMarkerImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CustomWrapImpl(
  offset: Int,
  val editor: EditorImpl,
  override val indent: Int,
  override val priority: Int,
) : RangeMarkerImpl(editor.elfDocument, offset, offset, false, true),
    CustomWrap {

  override val offset: Int
    get() = this.startOffset

  override fun toString(): String = "CustomWrapImpl[offset=$offset, indent=$indent]"

  override fun dispose() {
    super.dispose()
  }
}