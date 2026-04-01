// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap

import com.intellij.openapi.editor.SoftWrap
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SoftWrapEx : SoftWrap {
  fun advance(diff: Int)
  val isPaintable: Boolean
}