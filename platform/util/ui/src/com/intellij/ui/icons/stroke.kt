// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ColorUtil
import com.intellij.ui.svg.SvgAttributePatcher
import com.intellij.ui.svg.newSvgPatcher
import com.intellij.util.InsecureHashBuilder
import com.intellij.util.SVGLoader
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.awt.Color
import javax.swing.Icon

@Suppress("SpellCheckingInspection")
private val backgroundColors = listOf("#ebecf0", "#e7effd", "#dff2e0", "#f2fcf3", "#ffe8e8", "#fff5f5", "#fff8e3", "#fff4eb", "#eee0ff")
private val strokeColors = listOf("black", "#000000",
                                  "white", "#ffffff",
                                  "#818594",
                                  "#6c707e",
                                  "#3574f0",
                                  "#5fb865",
                                  "#e35252",
                                  "#eb7171",
                                  "#e3ae4d",
                                  "#fcc75b",
                                  "#f28c35",
                                  "#955ae0")

fun toStrokeIcon(original: Icon, resultColor: Color): Icon {
  val palettePatcher = getStrokePatcher(resultColor, strokeColors, backgroundColors)
  val strokeReplacer = getStrokePatcher(resultColor = resultColor, strokeColors = listOf("white", "#ffffff"), backgroundColors = listOf())
  return replaceCachedImageIcons(icon = original) { cachedImageIcon ->
    var icon = cachedImageIcon
    var patcher = palettePatcher
    val flags = icon.imageFlags
    if (flags and ImageDescriptor.HAS_STROKE == ImageDescriptor.HAS_STROKE) {
      val strokeIcon = icon.createStrokeIcon()
      @Suppress("UseJBColor")
      if (resultColor == Color.WHITE) {
        // will be nothing to patch actually
        return@replaceCachedImageIcons strokeIcon
      }

      if (strokeIcon is CachedImageIcon) {
        icon = strokeIcon
        patcher = strokeReplacer
      }
    }
    IconLoader.patchColorsInCacheImageIcon(imageIcon = icon, colorPatcher = patcher, isDark = false)
  }!!
}

private fun getStrokePatcher(resultColor: Color,
                             strokeColors: List<String>,
                             backgroundColors: List<String> = emptyList()): SVGLoader.SvgElementColorPatcherProvider {
  val fg = ColorUtil.toHtmlColor(resultColor)
  val map = strokeColors.associateWith { fg }
  val alpha = Object2IntOpenHashMap<String>(map.size)
  alpha.defaultReturnValue(Int.MIN_VALUE)
  for (s in map.values) {
    alpha.put(s, resultColor.alpha)
  }

  val digest = InsecureHashBuilder()
    .stringList(strokeColors)
    .stringList(backgroundColors)
    .update(fg)
    .update(resultColor.alpha)
    .build()
  return object : SVGLoader.SvgElementColorPatcherProvider {
    override fun attributeForPath(path: String?): SvgAttributePatcher? {
      return newSvgPatcher(digest = digest,
                           newPalette = map + backgroundColors.associateWith { "#00000000" },
                           alphaProvider = { color ->
                             alpha.getInt(color).takeIf { it != Int.MIN_VALUE }
                           })
    }

    override fun digest() = digest
  }
}
