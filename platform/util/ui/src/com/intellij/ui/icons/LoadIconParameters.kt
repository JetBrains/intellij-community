// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.util.SVGLoader
import org.jetbrains.annotations.ApiStatus
import java.awt.image.ImageFilter

@ApiStatus.Internal
data class LoadIconParameters(
  @JvmField val filters: List<ImageFilter>,
  @JvmField val isDark: Boolean,
  @JvmField val colorPatcher: SVGLoader.SvgElementColorPatcherProvider?,
  @JvmField val isStroke: Boolean
) {
  companion object {
    internal fun defaultParameters(isDark: Boolean): LoadIconParameters {
      return LoadIconParameters(filters = emptyList(),
                                isDark = isDark,
                                colorPatcher = null,
                                isStroke = false)
    }
  }
}
