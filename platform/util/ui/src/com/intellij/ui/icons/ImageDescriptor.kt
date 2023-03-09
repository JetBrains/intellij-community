// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.ui.svg.SvgCacheClassifier
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ImageDescriptor(
  @JvmField val pathTransform: (String, String) -> String,
  // initial scale factor
  @JvmField val scale: Float,
  @JvmField val isSvg: Boolean,
  @JvmField val isDark: Boolean = false,
  @JvmField val isStroke: Boolean = false,
) {
  companion object {
    const val HAS_2x = 1
    const val HAS_DARK = 2
    const val HAS_DARK_2x = 4
    const val HAS_STROKE = 8

    @JvmField
    internal val STROKE_RETINA = ImageDescriptor(pathTransform = { p, e -> "${p}_stroke.$e" },
                                                 scale = 2f,
                                                 isSvg = false,
                                                 isDark = false,
                                                 isStroke = true)

    @JvmField
    internal val STROKE_NON_RETINA = ImageDescriptor(pathTransform = { p, e -> "${p}_stroke.$e" },
                                                     scale = 1f,
                                                     isSvg = false,
                                                     isDark = false,
                                                     isStroke = true)
  }

  internal fun toSvgMapper(): SvgCacheClassifier = SvgCacheClassifier(scale = scale, isDark = isDark, isStroke = isStroke)

  override fun toString(): String = "scale: $scale, isSvg: $isSvg"
}