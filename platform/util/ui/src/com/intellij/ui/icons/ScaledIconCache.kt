// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.ui.drawImage
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import java.awt.*
import java.lang.ref.SoftReference
import java.util.concurrent.CancellationException
import javax.swing.Icon

private const val SCALED_ICON_CACHE_LIMIT = 8

@JvmField
internal var isIconActivated: Boolean = !GraphicsEnvironment.isHeadless()

internal class ScaledIconCache {
  private val cache = Long2ObjectLinkedOpenHashMap<SoftReference<Icon>>(SCALED_ICON_CACHE_LIMIT + 1)

  @Synchronized
  fun getCachedIcon(host: CachedImageIcon, gc: GraphicsConfiguration?, attributes: IconAttributes): Icon {
    val sysScale = JBUIScale.sysScale(gc)
    val pixScale = if (JreHiDpiUtil.isJreHiDPIEnabled()) sysScale * JBUIScale.scale(1f) else JBUIScale.scale(1f)
    val cacheKey = getCacheKey(pixScale, attributes)
    cache.getAndMoveToFirst(cacheKey)?.get()?.let {
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
    val cacheKey = getCacheKey(pixScale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat(), attributes)
    // don't worry that empty ref in the map, we compute and put a new icon by the same key, so no need to remove invalid entry
    cache.getAndMoveToFirst(cacheKey)?.get()?.let {
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
      cache.putAndMoveToFirst(cacheKey, SoftReference(icon))
      return icon
    }

    val icon = ScaledResultIcon(image = image, original = host, objectScale = scaleContext.getScale(ScaleType.OBJ_SCALE).toFloat())
    cache.putAndMoveToFirst(cacheKey, SoftReference(icon))
    if (cache.size > SCALED_ICON_CACHE_LIMIT) {
      cache.removeLast()
    }
    return icon
  }

  fun clear() {
    cache.clear()
  }
}

internal class ScaledResultIcon(@JvmField internal val image: Image,
                                private val original: CachedImageIcon,
                                private val objectScale: Float) : Icon, ReplaceableIcon {
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    drawImage(g = g, image = image, x = x, y = y, sourceBounds = null, op = null, observer = c)
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

private fun getCacheKey(pixScale: Float, cacheFlags: IconAttributes): Long {
  return packTwoIntToLong(pixScale.toRawBits(), cacheFlags.flags)
}

private fun packTwoIntToLong(v1: Int, v2: Int): Long {
  return (v1.toLong() shl 32) or (v2.toLong() and 0xffffffffL)
}
