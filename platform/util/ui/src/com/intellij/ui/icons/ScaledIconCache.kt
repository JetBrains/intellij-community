// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "LiftReturnOrAssignment")

package com.intellij.ui.icons

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.ScalableIcon
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.SystemProperties
import com.intellij.util.ui.drawImage
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import java.lang.ref.SoftReference
import javax.swing.Icon

private const val SCALED_ICONS_CACHE_LIMIT = 5

private val CACHED_IMAGE_MAX_SIZE: Long = (SystemProperties.getFloatProperty("ide.cached.image.max.size", 1.5f) * 1024 * 1024).toLong()

@ApiStatus.Internal
fun isIconTooLargeForCache(icon: Icon): Boolean {
  return (4L * icon.iconWidth * icon.iconHeight) > CACHED_IMAGE_MAX_SIZE
}

private fun key(context: ScaleContext): Long {
  return (context.getScale(DerivedScaleType.EFF_USR_SCALE).toFloat().toBits().toLong()  shl 32) or
    (context.getScale(ScaleType.SYS_SCALE).toFloat().toBits().toLong() and 0xffffffffL)
}

internal class ScaledIconCache {
  private val cache = Long2ObjectLinkedOpenHashMap<SoftReference<Icon>>(SCALED_ICONS_CACHE_LIMIT)

  /**
   * Retrieves the orig icon scaled by the provided scale.
   */
  @Synchronized
  fun getOrScaleIcon(scale: Float, host: CachedImageIcon, scaleContext: ScaleContext): Icon? {
    val cacheKey = key(scaleContext)
    // don't worry that empty ref in the map, we compute and put a new icon by the same key, so no need to remove invalid entry
    cache.getAndMoveToFirst(cacheKey)?.get()?.let {
      return it
    }

    val image = host.loadImage(scaleContext = scaleContext, isDark = host.isDark) ?: return null

    // image wasn't loaded or broken
    val width = image.getWidth(null)
    val height = image.getHeight(null)
    if (width < 1 || height < 1) {
      logger<ScaledResultIcon>().error("Invalid icon: $host")
      return EMPTY_ICON
    }

    val icon = ScaledResultIcon(image = image, original = host, scale = scale)
    if ((4L * width * height) <= CACHED_IMAGE_MAX_SIZE) {
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

@TestOnly
@ApiStatus.Internal
fun getRealImage(icon: Icon): Image {
  val image = (icon as ScaledResultIcon).image
  return (image as? JBHiDPIScaledImage)?.delegate ?: image
}

internal class ScaledResultIcon(@JvmField internal val image: Image,
                                private val original: CachedImageIcon,
                                private val scale: Float) : Icon, ReplaceableIcon {
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    drawImage(g = g, image = image, x = x, y = y, sourceBounds = null, op = null, observer = c)
  }

  override fun getIconWidth(): Int = image.getWidth(null)

  override fun getIconHeight(): Int = image.getHeight(null)

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
