/*
 * Copyright (C) 2015 The Android Open Source Project
 * Modified 2026 by JetBrains s.r.o.
 * Copyright (C) 2026 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.resources.vectorDrawable.rendering

import org.w3c.dom.NamedNodeMap
import java.awt.Graphics2D
import java.awt.geom.AffineTransform

/**
 * Based on [com.android.ide.common.vectordrawable.VdElement]
 * Java source: https://cs.android.com/android-studio/platform/tools/base/+/7406bf062c551d16620f84cd4b9a5f12a5043cdf:sdk-common/src/main/java/com/android/ide/common/vectordrawable/VdElement.java
 *
 * Key differences from Android original:
 * - Added [parseColorValue] helper (shared by VdPath and VdTree)
 */
internal abstract class ComposeResourcesVdElement {
  var name: String? = null
  var isClipPath: Boolean = false

  abstract val isGroup: Boolean

  abstract fun draw(g: Graphics2D, currentMatrix: AffineTransform, scaleX: Float, scaleY: Float)

  abstract fun parseAttributes(attributes: NamedNodeMap)

  companion object {
    /** Parses a color string of the forms into a packed ARGB int. */
    fun parseColorValue(color: String): Int {
      if (!color.startsWith("#")) return 0

      val hex = color.substring(1)

      val fullHex = when (hex.length) {
        3 -> "FF${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}" //#RGB
        4 -> "${hex[0]}${hex[0]}${hex[1]}${hex[1]}${hex[2]}${hex[2]}${hex[3]}${hex[3]}" //#ARGB
        6 -> "FF$hex" // #RRGGBB
        8 -> hex // #AARRGGBB
        else -> return 0
      }

      return fullHex.toLongOrNull(16)?.toInt() ?: 0
    }
  }
}