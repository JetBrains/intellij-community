// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "LiftReturnOrAssignment")

package com.intellij.ui.icons

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.ScalableIcon
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.ui.StartupUiUtil
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import java.awt.*
import java.lang.ref.SoftReference
import java.util.concurrent.CancellationException
import javax.swing.Icon

private const val SCALED_ICON_CACHE_LIMIT = 8

@JvmField
internal var isIconActivated: Boolean = !GraphicsEnvironment.isHeadless()

internal class ScaledIconCache {
  private var cache: Long2ObjectLinkedOpenHashMap<SoftReference<Icon>>? = null

  @Synchronized
  fun getCachedIcon(host: CachedImageIcon, gc: GraphicsConfiguration?, attributes: IconAttributes): Icon {
    val sysScale = JBUIScale.sysScale(gc)
    val pixScale = if (JreHiDpiUtil.isJreHiDPIEnabled()) sysScale * JBUIScale.scale(1f) else JBUIScale.scale(1f)
    val cacheKey = getCacheKey(pixScale, sysScale, attributes)
    cache?.getAndMoveToFirst(cacheKey)?.get()?.let {
      return it
    }

    if (!isIconActivated) {
      return EMPTY_ICON
    }

    val scaleContext = ScaleContext.create(ScaleType.SYS_SCALE.of(sysScale))
    return loadIcon(host = host, scaleContext = scaleContext, cacheKey = cacheKey, attributes = attributes)
  }

  @Synchronized
  fun getOrScaleIcon(host: CachedImageIcon, scaleContext: ScaleContext, attributes: IconAttributes): Icon {
    val cacheKey = getCacheKey(
      pixScale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat(),
      sysScale = scaleContext.getScale(ScaleType.SYS_SCALE).toFloat(),
      attributes
    )
    // don't worry that empty ref in the map, we compute and put a new icon by the same key, so no need to remove invalid entry
    cache?.getAndMoveToFirst(cacheKey)?.get()?.let {
      return it
    }
    return loadIcon(host = host, scaleContext = scaleContext, cacheKey = cacheKey, attributes = attributes)
  }

  private fun loadIcon(host: CachedImageIcon, scaleContext: ScaleContext, cacheKey: Long, attributes: IconAttributes): Icon {
    val image = try {
      host.loadImage(scaleContext = scaleContext, attributes = attributes) ?: return EMPTY_ICON
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      logger<ScaledIconCache>().error(e)

      // cache it - don't try to load it again and again
      val icon = EMPTY_ICON
      getOrCreateCache().putAndMoveToFirst(cacheKey, SoftReference(icon))
      return icon
    }

    val icon = ScaledResultIcon(image = image, original = host, objectScale = scaleContext.getScale(ScaleType.OBJ_SCALE).toFloat())
    val cache = getOrCreateCache()
    cache.putAndMoveToFirst(cacheKey, SoftReference(icon))
    if (cache.size > SCALED_ICON_CACHE_LIMIT) {
      cache.removeLast()
    }
    return icon
  }

  private fun getOrCreateCache(): Long2ObjectLinkedOpenHashMap<SoftReference<Icon>> {
    var cache = cache
    if (cache == null) {
      cache = Long2ObjectLinkedOpenHashMap<SoftReference<Icon>>(SCALED_ICON_CACHE_LIMIT + 1)
      this.cache = cache
    }
    return cache
  }
  fun clear() {
    cache = null
  }
}

internal class ScaledResultIcon(@JvmField internal val image: Image,
                                private val original: CachedImageIcon,
                                private val objectScale: Float) : Icon, ReplaceableIcon {
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    StartupUiUtil.drawImage(g, image, x, y, sourceBounds = null, op = null, observer = c)
  }

  override fun getIconWidth(): Int = image.getWidth(null)

  override fun getIconHeight(): Int = image.getHeight(null)

  override fun replaceBy(replacer: IconReplacer): Icon {
    val originalReplaced = replacer.replaceIcon(original)
    if (originalReplaced is ScalableIcon) {
      return originalReplaced.scale(objectScale)
    }
    else {
      logger<ScaledResultIcon>().error("The result after replacing cannot be scaled: $originalReplaced")
      return this
    }
  }

  override fun toString(): String = "ScaledResultIcon for $original"
}

private fun getCacheKey(pixScale: Float, sysScale: Float, cacheFlags: IconAttributes): Long {
  // The pixScale is the effective scale combining everything, and it determines the size of the actual raster to produce.
  // However, it's not enough to use just it for caching, because an image is not just a raster,
  // but also a combination of certain properties that determine its user-space size and how it's rendered without losing image quality.
  // For example, if the user scale = 150% and sys scale = 100%, then an image with the original size of 16x16 will have
  // both the user-space and actual sizes of 24x24, and it'll be an instance of BufferedImage.
  // However, if the user scale = 100% and sys scale = 150%, then the same image will have the user-space size of 16x16,
  // but the actual size will be 24x24, and it'll be an instance of JBHiDPIScaledImage to render properly.
  // The effective pixScale in both cases will be 150%, however, so we can't rely on it alone.
  // That's why we pack both pixScale and sysScale here, ignoring the lower part as scaling factors don't need that much precision anyway.
  return packTwoIntToLong(
    packTwoShortsToInt(
      pixScale.toRawBits().mostSignificantHalf(),
      sysScale.toRawBits().mostSignificantHalf(),
    ),
    cacheFlags.flags
  )
}

private fun Int.mostSignificantHalf(): Int = (this shr 16) and 0xFFFF

private fun packTwoShortsToInt(v1: Int, v2: Int): Int = (v1 shl 16) or v2

private fun packTwoIntToLong(v1: Int, v2: Int): Long {
  return (v1.toLong() shl 32) or (v2.toLong() and 0xffffffffL)
}
