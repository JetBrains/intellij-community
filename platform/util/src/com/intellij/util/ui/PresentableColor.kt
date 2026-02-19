// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

@ApiStatus.Internal
interface PresentableColor {
  fun getPresentableName(): @NlsSafe String?

  companion object {
    @JvmStatic
    fun toPresentableString(color: Color?): String {
      if (color == null) return "null"
      return (color as? PresentableColor)?.getPresentableName() ?: color.toString()
    }
  }
}
