// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "LiftReturnOrAssignment")

package com.intellij.ui.icons

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.reference.SoftReference
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.ui.drawImage
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.ImageIcon

private const val SCALED_ICONS_CACHE_LIMIT = 5

@ApiStatus.Internal
fun isIconTooLargeForCache(icon: Icon): Boolean {
  return (4L * icon.iconWidth * icon.iconHeight) > CACHED_IMAGE_MAX_SIZE
}

private fun key(context: ScaleContext): Long {
  return (context.getScale(DerivedScaleType.EFF_USR_SCALE).toFloat().toBits().toLong() shl 32) or
    (context.getScale(ScaleType.SYS_SCALE).toFloat().toBits().toLong() and 0xffffffffL)
}

internal class ScaledIconCache {
  private val cache = Long2ObjectLinkedOpenHashMap<SoftReference<ImageIcon>>(SCALED_ICONS_CACHE_LIMIT)

  /**
   * Retrieves the orig icon scaled by the provided scale.
   */
  @Synchronized
  fun getOrScaleIcon(scale: Float, host: CachedImageIcon, scaleContext: ScaleContext): ImageIcon? {
    val cacheKey = key(scaleContext)
    // don't worry that empty ref in the map, we compute and put a new icon by the same key, so no need to remove invalid entry
    cache.getAndMoveToFirst(cacheKey)?.get()?.let {
      return it
    }

    val image = host.loadImage(scaleContext = scaleContext, isDark = host.isDark) ?: return null
    val icon = createScaledIcon(image = image, host = host, scale = scale)
    if (icon != null && !isIconTooLargeForCache(icon)) {
      cache.putAndMoveToFirst(cacheKey, SoftReference(icon))
      if (cache.size > SCALED_ICONS_CACHE_LIMIT) {
        cache.removeLast()
      }
    }
    return icon
  }

  @Synchronized
  fun clear() {
    cache.clear()
  }
}

private class ScaledResultIcon(image: Image,
                               private val original: CachedImageIcon,
                               private val scale: Float) : ImageIcon(image), ReplaceableIcon {
  @Synchronized
  override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
    drawImage(g = g!!, image = image, dstBounds = Rectangle(x, y, -1, -1), srcBounds = null, op = null, observer = imageObserver ?: c)
  }

  override fun replaceBy(replacer: IconReplacer): Icon {
    val originalReplaced = replacer.replaceIcon(original)
    if (originalReplaced is ScalableIcon) {
      return originalReplaced.scale(scale)
    }
    else {
      logger<ScaledResultIcon>().error("The result after replacing cannot be scaled: $originalReplaced")
      return this
    }
  }

  override fun toString(): String = "ScaledResultIcon for $original"
}

private fun createScaledIcon(image: Image, host: CachedImageIcon, scale: Float): ImageIcon? {
  // image wasn't loaded or broken
  if (image.getHeight(null) < 1) {
    return null
  }

  val icon = ScaledResultIcon(image = image, original = host, scale = scale)
  if (!IconLoader.isGoodSize(icon)) {
    // # 22481
    logger<ScaledResultIcon>().error("Invalid icon: $host")
    return CachedImageIcon.EMPTY_ICON
  }
  return icon
}
