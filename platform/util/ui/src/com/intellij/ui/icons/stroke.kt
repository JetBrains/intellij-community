// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.dynatrace.hash4j.hashing.HashFunnel
import com.dynatrace.hash4j.hashing.Hashing
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.ui.ColorUtil
import com.intellij.ui.svg.SvgAttributePatcher
import com.intellij.ui.svg.newSvgPatcher
import com.intellij.ui.svg.replaceCachedImageIcons
import com.intellij.util.SVGLoader
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.awt.Color
import javax.swing.Icon
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

@Suppress("SpellCheckingInspection")
private val backgroundColors = pairWithDigest(listOf(
  "#ebecf0", "#e7effd", "#dff2e0", "#f2fcf3", "#ffe8e8", "#fff5f5", "#fff8e3", "#fff4eb", "#eee0ff",
))
private val strokeColors = pairWithDigest(listOf(
  "black", "#000000",
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
  "#955ae0",
))

private val strokeColorsForReplacer = pairWithDigest(listOf("white", "#ffffff"))

private fun pairWithDigest(list: List<String>): Pair<List<String>, Long> {
  return list to Hashing.komihash5_0().hashStream().putOrderedIterable(list, HashFunnel.forString()).asLong
}

internal class IconAndColorCacheKey(@JvmField val icon: Icon, @JvmField val color: Color) {
  // hashCode for JBColor depends on isDark, so, use int
  private val rgb: Int = color.rgb

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    return other is IconAndColorCacheKey && rgb == other.rgb && icon == other.icon
  }

  override fun hashCode(): Int {
    return 31 * icon.hashCode() + rgb
  }
}

private val strokeIconCache = Caffeine.newBuilder()
  .maximumSize(64)
  .executor(Dispatchers.Default.asExecutor())
  .expireAfterAccess(30.minutes.toJavaDuration())
  .build<IconAndColorCacheKey, Icon> { computeStrokeIcon(original = it.icon, resultColor = it.color) }
  .also {
    registerIconCacheCleaner(it::invalidateAll)
  }

fun toStrokeIcon(icon: Icon, resultColor: Color): Icon {
  return strokeIconCache.get(IconAndColorCacheKey(icon = icon, color = resultColor))
}

private fun computeStrokeIcon(original: Icon, resultColor: Color): Icon {
  val palettePatcher = getStrokePatcher(resultColor = resultColor, strokeColors = strokeColors, backgroundColors = backgroundColors)
  val strokeReplacer = getStrokePatcher(resultColor = resultColor, strokeColors = strokeColorsForReplacer, backgroundColors = null)
  return replaceCachedImageIcons(icon = original) { cachedImageIcon ->
    replaceCachedImageIcon(icon = cachedImageIcon,
                           palettePatcher = palettePatcher,
                           resultColor = resultColor,
                           strokeReplacer = strokeReplacer)
  }!!
}

private fun replaceCachedImageIcon(icon: CachedImageIcon,
                                   palettePatcher: SVGLoader.SvgElementColorPatcherProvider,
                                   resultColor: Color,
                                   strokeReplacer: SVGLoader.SvgElementColorPatcherProvider): Icon {
  if (icon.imageFlags and ImageDescriptor.HAS_STROKE == ImageDescriptor.HAS_STROKE) {
    @Suppress("UseJBColor")
    if (resultColor == Color.WHITE) {
      // will be nothing to patch actually
      return icon.createStrokeIcon()
    }
    else {
      return icon.createWithPatcher(colorPatcher = strokeReplacer, useStroke = true, isDark = false)
    }
  }
  else {
    return icon.createWithPatcher(colorPatcher = palettePatcher, isDark = false)
  }
}

private fun getStrokePatcher(resultColor: Color,
                             strokeColors: Pair<List<String>, Long>,
                             backgroundColors: Pair<List<String>, Long>?): SVGLoader.SvgElementColorPatcherProvider {
  val fg = ColorUtil.toHtmlColor(resultColor)
  val map = strokeColors.first.associateWith { fg }
  val alpha = Object2IntOpenHashMap<String>(map.size)
  alpha.defaultReturnValue(Int.MIN_VALUE)
  for (s in map.values) {
    alpha.put(s, resultColor.alpha)
  }

  val digest = longArrayOf(
    strokeColors.second,
    backgroundColors?.second ?: 0,
    resultColor.rgb.toLong(),
  )
  return object : SVGLoader.SvgElementColorPatcherProvider {
    override fun digest() = digest

    override fun attributeForPath(path: String): SvgAttributePatcher? {
      val newPalette = map + (backgroundColors?.first?.associateWith { "#00000000" } ?: emptyMap())
      if (newPalette.isEmpty()) {
        return null
      }

      return newSvgPatcher(newPalette = newPalette, alphaProvider = { color ->
                             alpha.getInt(color).takeIf { it != Int.MIN_VALUE }
                           })
    }
  }
}