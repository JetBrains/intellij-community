// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.dynatrace.hash4j.hashing.HashFunnel
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.hasher
import com.intellij.ui.svg.SvgAttributePatcher
import com.intellij.ui.svg.newSvgPatcher
import com.intellij.util.SVGLoader
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.awt.Color
import javax.swing.Icon
import kotlin.time.Duration.Companion.hours
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
  return list to hasher.hashStream().putOrderedIterable(list, HashFunnel.forString()).asLong
}

// hashCode for JBColor depends on isDark, so, a custom logic
internal class IconAndColorCacheKey(@JvmField val icon: Icon, @JvmField val color: Color) {
  private val rgb: Int = color.rgb
  private val isDark: Boolean = color is JBColor && !JBColor.isBright()

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    return other is IconAndColorCacheKey && rgb == other.rgb && icon == other.icon && isDark == other.isDark
  }

  override fun hashCode(): Int {
    var result = icon.hashCode()
    result = 31 * result + rgb
    result = 31 * result + isDark.hashCode()
    return result
  }
}

private val strokeIconCache = Caffeine.newBuilder()
  .maximumSize(64)
  .expireAfterAccess(1.hours.toJavaDuration())
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

  if (original is CachedImageIcon) {
    return replaceCachedImageIcon(cachedImageIcon = original,
                                  palettePatcher = palettePatcher,
                                  resultColor = resultColor,
                                  strokeReplacer = strokeReplacer)
  }
  else {
    return replaceCachedImageIcons(icon = original) { cachedImageIcon ->
      replaceCachedImageIcon(cachedImageIcon = cachedImageIcon,
                             palettePatcher = palettePatcher,
                             resultColor = resultColor,
                             strokeReplacer = strokeReplacer)
    }!!
  }
}

private fun replaceCachedImageIcon(cachedImageIcon: CachedImageIcon,
                                   palettePatcher: SVGLoader.SvgElementColorPatcherProvider,
                                   resultColor: Color,
                                   strokeReplacer: SVGLoader.SvgElementColorPatcherProvider): Icon {
  var icon = cachedImageIcon
  var colorPatcher = palettePatcher
  val flags = icon.imageFlags
  if (flags and ImageDescriptor.HAS_STROKE == ImageDescriptor.HAS_STROKE) {
    icon = icon.createStrokeIcon()
    @Suppress("UseJBColor")
    if (resultColor == Color.WHITE) {
      // will be nothing to patch actually
      return icon
    }
    colorPatcher = strokeReplacer
  }

  var result = icon
  val variant = result.getDarkIcon(false)
  if (variant is CachedImageIcon) {
    result = variant
  }
  return result.createWithPatcher(colorPatcher)
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
    packTwoIntToLong(resultColor.rgb, resultColor.alpha),
  )
  return object : SVGLoader.SvgElementColorPatcherProvider {
    override fun digest() = digest

    override fun attributeForPath(path: String): SvgAttributePatcher? {
      val newPalette = map + (backgroundColors?.first?.associateWith { "#00000000" } ?: emptyMap())
      if (newPalette.isEmpty()) {
        return null
      }

      return newSvgPatcher(newPalette = newPalette,
                           alphaProvider = { color ->
                             alpha.getInt(color).takeIf { it != Int.MIN_VALUE }
                           })
    }
  }
}

fun packTwoIntToLong(v1: Int, v2: Int): Long {
  return (v1.toLong() shl 32) or (v2.toLong() and 0xffffffffL)
}
