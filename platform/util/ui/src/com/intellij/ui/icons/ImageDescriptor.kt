// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.ui.svg.SvgCacheMapper
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ImageDescriptor(
  @JvmField val path: String,
  // initial scale factor
  @JvmField val scale: Float,
  @JvmField val isSvg: Boolean,
  @JvmField val isDark: Boolean,
  @JvmField val isStroke: Boolean,
) {
  companion object {
    const val HAS_2x = 1
    const val HAS_DARK = 2
    const val HAS_DARK_2x = 4
    const val HAS_STROKE = 8
  }

  init {
    assert(!path.isEmpty())
  }

  internal fun toSvgMapper(): SvgCacheMapper = SvgCacheMapper(scale = scale, isDark = isDark, isStroke = isStroke)

  override fun toString(): String = "$path, scale: $scale, isSvg: $isSvg"
}