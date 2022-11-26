// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.ui.scale.ScaleContext
import com.intellij.util.SVGLoader
import org.jetbrains.annotations.ApiStatus
import java.awt.image.ImageFilter

@ApiStatus.Internal
data class LoadIconParameters(
  @JvmField val filters: List<ImageFilter>,
  @JvmField val scaleContext: ScaleContext,
  @JvmField val isDark: Boolean,
  @JvmField val colorPatcher: SVGLoader.SvgElementColorPatcherProvider?,
  @JvmField val isStroke: Boolean
) {
  companion object {
    @JvmStatic
    fun defaultParameters(isDark: Boolean): LoadIconParameters = LoadIconParameters(emptyList(), ScaleContext.create(), isDark, null, false)
  }
}
