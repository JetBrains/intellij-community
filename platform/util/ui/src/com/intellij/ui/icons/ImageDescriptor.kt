// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ConstPropertyName")

package com.intellij.ui.icons

import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.svg.SvgCacheClassifier
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class ImageDescriptor(
  @JvmField val pathTransform: (String, String) -> String,
  // initial scale factor
  @JvmField val scale: Float,
  @JvmField val isSvg: Boolean,
  @JvmField val isDark: Boolean = false,
  @JvmField val isStroke: Boolean = false,
) {
  companion object {
    const val HAS_2x: Int = 1
    const val HAS_DARK: Int = 2
    const val HAS_DARK_2x: Int = 4
    const val HAS_STROKE: Int = 8

    @JvmField
    internal val STROKE_RETINA: ImageDescriptor = ImageDescriptor(pathTransform = { p, e -> "${p}_stroke.$e" },
                                                                  scale = 2f,
                                                                  isSvg = false,
                                                                  isDark = false,
                                                                  isStroke = true)

    @JvmField
    internal val STROKE_NON_RETINA: ImageDescriptor = ImageDescriptor(pathTransform = { p, e -> "${p}_stroke.$e" },
                                                                      scale = 1f,
                                                                      isSvg = false,
                                                                      isDark = false,
                                                                      isStroke = true)
  }

  internal fun toSvgMapper(): SvgCacheClassifier = SvgCacheClassifier(scale = scale, isDark = isDark, isStroke = isStroke)

  override fun toString(): String = "scale: $scale, isSvg: $isSvg"
}

@Internal
fun createImageDescriptorList(path: String, isDark: Boolean, pixScale: Float): List<ImageDescriptor> {
  // prefer retina images for HiDPI scale, because downscaling retina images provide a better result than up-scaling non-retina images
  if (!path.startsWith(FILE_SCHEME_PREFIX) && path.contains("://")) {
    val qI = path.lastIndexOf('?')
    val isSvg = (if (qI == -1) path else path.substring(0, qI)).endsWith(".svg", ignoreCase = true)
    return listOf(ImageDescriptor(pathTransform = { p, e -> "$p.$e" }, scale = 1f, isSvg = isSvg, isDark = isDark, isStroke = false))
  }

  val isSvg = path.endsWith(".svg")
  val isRetina = pixScale != 1f

  val list = ArrayList<ImageDescriptor>(5)
  if (!isSvg) {
    list.add(if (isRetina) ImageDescriptor.STROKE_RETINA else ImageDescriptor.STROKE_NON_RETINA)
    addFileNameVariant(isRetina = isRetina, isDark = isDark, isSvg = false, scale = pixScale, list = list)
  }

  addFileNameVariant(isRetina = isRetina, isDark = isDark, isSvg = isSvg, scale = pixScale, list = list)

  // fallback to non-dark
  if (isDark) {
    addFileNameVariant(isRetina = isRetina, isDark = false, isSvg = isSvg, scale = pixScale, list = list)
    if (!isSvg) {
      addFileNameVariant(isRetina = false, isDark = false, isSvg = true, scale = pixScale, list = list)
    }
  }
  return list
}

// @2x is used even for SVG icons by intention
private fun addFileNameVariant(isRetina: Boolean,
                               isDark: Boolean,
                               isSvg: Boolean,
                               scale: Float,
                               list: MutableList<ImageDescriptor>) {
  val retinaScale = if (isSvg) scale else 2f
  val nonRetinaScale = if (isSvg) scale else 1f
  if (isDark) {
    val d1 = ImageDescriptor(pathTransform = { p, e -> "${p}@2x_dark.$e" }, scale = retinaScale, isSvg = isSvg, isDark = true)
    val d2 = ImageDescriptor(pathTransform = { p, e -> "${p}_dark@2x.$e" }, scale = retinaScale, isSvg = isSvg, isDark = true)
    val d3 = ImageDescriptor(pathTransform = { p, e -> "${p}_dark.$e" }, scale = nonRetinaScale, isSvg = isSvg, isDark = true)

    if (isRetina) {
      list.add(d1)
      list.add(d2)
      list.add(d3)
    }
    else {
      list.add(d3)
      list.add(d2)
      list.add(d1)
    }
  }
  else {
    val d1 = ImageDescriptor(pathTransform = { p, e -> "${p}@2x.$e" }, scale = retinaScale, isSvg = isSvg)
    val d2 = ImageDescriptor(pathTransform = { p, e -> "${p}.$e" }, scale = nonRetinaScale, isSvg = isSvg)

    if (isRetina) {
      list.add(d1)
      list.add(d2)
    }
    else {
      list.add(d2)
      list.add(d1)
    }
  }
}

@Internal
fun getImageDescriptors(path: String, isDark: Boolean, scaleContext: ScaleContext): List<ImageDescriptor> {
  return createImageDescriptorList(path = path,
                                   isDark = isDark,
                                   pixScale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat())
}
