// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.openapi.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.reference.SoftReference
import com.intellij.ui.IconReplacer
import com.intellij.ui.icons.ReplaceableIcon
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.ImageLoader
import com.intellij.util.containers.FixedHashMap
import com.intellij.util.ui.JBImageIcon
import java.awt.Image
import java.util.*
import javax.swing.Icon
import javax.swing.ImageIcon

private const val SCALED_ICONS_CACHE_LIMIT = 5

private fun key(context: ScaleContext): Long {
  return java.lang.Float.floatToIntBits(context.getScale(DerivedScaleType.EFF_USR_SCALE).toFloat()).toLong() shl 32 or
    (java.lang.Float.floatToIntBits(context.getScale(ScaleType.SYS_SCALE).toFloat()).toLong() and 0xffffffffL)
}

internal class ScaledIconCache(private val host: CachedImageIcon) {
  private val cache = Collections.synchronizedMap(FixedHashMap<Long, SoftReference<ImageIcon>>(SCALED_ICONS_CACHE_LIMIT))

  /**
   * Retrieves the orig icon scaled by the provided scale.
   */
  fun getOrScaleIcon(scale: Float): ImageIcon? {
    var scaleContext = host.scaleContext
    if (scale != 1f) {
      scaleContext = scaleContext.copy()
      scaleContext.setScale(ScaleType.OBJ_SCALE.of(scale.toDouble()))
    }

    val cacheKey = key(scaleContext)
    cache.get(cacheKey)?.get()?.let {
      return it
    }

    val image = host.loadImage(scaleContext = scaleContext, isDark = host.isDark) ?: return null
    val icon = createScaledIcon(image = image, cii = host, scale = scale)
    if (icon != null && !ImageLoader.isIconTooLargeForCache(icon)) {
      cache.put(cacheKey, SoftReference(icon))
    }
    return icon
  }

  fun clear() {
    cache.clear()
  }
}

private class ScaledResultIcon(image: Image,
                               private val original: CachedImageIcon,
                               private val scale: Float) : JBImageIcon(image), ReplaceableIcon {
  override fun replaceBy(replacer: IconReplacer): Icon {
    val originalReplaced = replacer.replaceIcon(original)
    return if (originalReplaced is ScalableIcon) {
      originalReplaced.scale(scale)
    }
    else {
      logger<ScaledResultIcon>().error("The result after replacing cannot be scaled: $originalReplaced")
      this
    }
  }
}

private fun createScaledIcon(image: Image, cii: CachedImageIcon, scale: Float): ImageIcon? {
  // image wasn't loaded or broken
  if (image.getHeight(null) < 1) {
    return null
  }

  val icon = ScaledResultIcon(image, cii, scale)
  if (!IconLoader.isGoodSize(icon)) {
    // # 22481
    logger<ScaledResultIcon>().error("Invalid icon: $cii")
    return CachedImageIcon.EMPTY_ICON
  }
  return icon
}
