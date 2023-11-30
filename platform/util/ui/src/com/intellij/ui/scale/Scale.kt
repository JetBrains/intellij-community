// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui.scale

import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.scale.ScaleType.*
import it.unimi.dsi.fastutil.doubles.Double2ObjectMap
import it.unimi.dsi.fastutil.doubles.Double2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import java.util.function.DoubleFunction


/**
 * The IDE supports two different HiDPI modes:
 *
 * 1) IDE-managed HiDPI mode.
 * Supported for backward compatibility until complete transition to the JRE-managed HiDPI mode happens.
 * In this mode there's a single coordinate space, and the whole UI is scaled by the IDE guided by the user scale factor ([USR_SCALE]).
 *
 * 2) JRE-managed HiDPI mode.
 * In this mode, the JRE scales graphics prior to drawing it on the device.
 * So, there are two coordinate spaces: the user space and the device space.
 * The system scale factor ([SYS_SCALE]) defines the transform b/w the spaces.
 * The UI size metrics (windows, controls, font height) are in the user coordinate space.
 * Though, the raster images should be aware of the device scale to meet HiDPI.
 * (For instance, JRE on a Mac Retina monitor device works in the JRE-managed HiDPI mode,
 * transforming graphics to the double-scaled device coordinate space)
 *
 * The IDE operates the scale factors of the following types:
 * 1) The user scale factor: [USR_SCALE]
 * 2) The system (monitor device) scale factor: [SYS_SCALE]
 * 3) The object (UI instance specific) scale factor: [OBJ_SCALE]
 *
 * @see com.intellij.ui.JreHiDpiUtil.isJreHiDPIEnabled
 * @see com.intellij.util.ui.UIUtil.isJreHiDPI
 * @see JBUIScale.isUsrHiDPI
 * @see com.intellij.util.ui.UIUtil.drawImage
 * @see com.intellij.util.ui.ImageUtil.createImage
 * @see ScaleContext
 *
 * @author tav
 */
enum class ScaleType {
  /**
   * The user scale factor is set and managed by the IDE.
   * Currently, it's derived from the UI font size, specified in the IDE Settings.
   *
   * The user scale value depends on which HiDPI mode is enabled.
   * In the IDE-managed HiDPI mode the user scale "includes" the default system scale and simply equals it with the default UI font size.
   * In the JRE-managed HiDPI mode the user scale is independent of the system scale and equals 1.0 with the default UI font size.
   * In case the default UI font size changes, the user scale changes proportionally in both the HiDPI modes.
   *
   * In the IDE-managed HiDPI mode the user scale completely defines the UI scale.
   * In the JRE-managed HiDPI mode the user scale can be considered a supplementary scale taking effect in cases like
   * the IDE Presentation Mode and when the default UI scale is changed by the user.
   *
   * @see JBUIScale.setUserScaleFactor
   * @see JBUIScale.scale
   */
  USR_SCALE,

  /**
   * The system scale factor is defined by the device DPI and/or the system settings.
   * For instance, a Mac Retina monitor device has the system scale 2.0 by default.
   * As there can be multiple devices (multi-monitor configuration) there can be multiple system scale factors, appropriately.
   * However, there's always a single default system scale factor corresponding to the default device.
   * And it's the only system scale available in the IDE-managed HiDPI mode.
   *
   * In the JRE-managed HiDPI mode, the system scale defines the scale of the transform b/w the user
   * and the device coordinates spaces performed by the JRE.
   */
  SYS_SCALE,

  /**
   * An extra scale factor of a particular UI object, which doesn't affect any other UI object, as opposed
   * to the user scale and the system scale factors. This scale factor affects the user space size of the object
   * and doesn't depend on the HiDPI mode. By default, it is set to 1.0.
   */
  OBJ_SCALE;

  fun of(value: Double): Scale = scaleOf(value = value, type = this)

  @Internal
  fun of(value: Float): Scale {
    return simpleCache.get().computeIfAbsent(cacheKey(value = value, type = this), Long2ObjectFunction { key ->
      Scale(value = Float.fromBits((key shr 32).toInt()).toDouble(), type = ScaleType.values()[key.toInt()])
    })
  }
}

/**
 * A scale factor value of [ScaleType].
 *
 * @author tav
 */
@Internal
data class Scale(@JvmField val value: Double, @JvmField val type: ScaleType)

// the cache radically reduces potential thousands of equal Scale instances
private val cache = ThreadLocal.withInitial {
  EnumMap<ScaleType, Double2ObjectMap<Scale>>(ScaleType::class.java)
}

private fun cacheKey(value: Float, type: ScaleType): Long {
  return (value.toBits().toLong() shl 32) or (type.ordinal.toLong() and 0xffffffffL)
}

// the cache radically reduces potential thousands of equal Scale instances
private val simpleCache = ThreadLocal.withInitial {
  Long2ObjectOpenHashMap<Scale>()
}

private fun scaleOf(value: Double, type: ScaleType): Scale {
  return cache.get()
    .computeIfAbsent(type) { Double2ObjectOpenHashMap() }
    .computeIfAbsent(value, DoubleFunction { Scale(value, type) })
}

// The scale below 1.0 is impractical.
// It's rather accepted for debug purpose.
// Treat it as "hidpi" to correctly manage images which have different users and real size
// (for scale below 1.0 the real size will be smaller).
internal fun isHiDPI(scale: Float): Boolean = scale != 1f

internal fun isHiDPIEnabledAndApplicable(scale: Float): Boolean = isHiDPI(scale) && JreHiDpiUtil.isJreHiDPIEnabled()