// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.customwrap

import com.intellij.openapi.editor.CustomWrap
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.impl.CustomWrapModelImpl
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.RangeMarkerImpl
import org.jetbrains.annotations.ApiStatus

internal class CustomWrapImpl(
  offset: Int,
  document: DocumentEx,
  val model: CustomWrapModelImpl,
  override val indent: Int,
  override val priority: Int,
) : RangeMarkerImpl(document, offset, offset, false, true),
    CustomWrap {

  override val offset: Int
    get() = this.startOffset

  override fun toString(): String = "CustomWrapImpl[offset=$offset, indent=$indent]"

  override fun dispose() {
    super.dispose()
  }
}