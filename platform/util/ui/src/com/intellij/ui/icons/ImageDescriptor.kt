// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ConstPropertyName")

package com.intellij.ui.icons

import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
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
  }

  internal fun toSvgMapper(): SvgCacheClassifier = SvgCacheClassifier(scale = scale, isDark = isDark, isStroke = isStroke)

  override fun toString(): String = "scale: $scale, isSvg: $isSvg"
}

@Internal
fun createImageDescriptorList(path: String, isDark: Boolean, isStroke: Boolean, pixScale: Float): List<ImageDescriptor> {
  // prefer retina images for HiDPI scale, because downscaling retina images provide a better result than up-scaling non-retina images
  if (!path.startsWith(FILE_SCHEME_PREFIX) && path.contains("://")) {
    val qI = path.lastIndexOf('?')
    val isSvg = (if (qI == -1) path else path.substring(0, qI)).endsWith(".svg", ignoreCase = true)
    return listOf(ImageDescriptor(SuffixPathTransform(""), scale = 1f, isSvg = isSvg, isDark = isDark, isStroke = false))
  }

  val isSvg = path.endsWith(".svg")
  val isRetina = pixScale != 1f

  val list = ArrayList<ImageDescriptor>(5)

  if (isStroke) {
    addFileNameVariant(isRetina = isRetina, isDark = false, isSvg = isSvg, isStroke = true, scale = pixScale, list = list)
  }

  if (!isSvg) {
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
                               isStroke: Boolean = false,
                               scale: Float,
                               list: MutableList<ImageDescriptor>) {
  val retinaScale = if (isSvg) scale else 2f
  val nonRetinaScale = if (isSvg) scale else 1f
  if (isStroke) {
    val strokeScale = if (isRetina) retinaScale else nonRetinaScale
    val d = ImageDescriptor(SuffixPathTransform("_stroke"), scale = strokeScale, isSvg = isSvg, isDark = isDark, isStroke = true)
    list.add(d)
  }
  else if (isDark) {
    val d1 = ImageDescriptor(SuffixPathTransform("@2x_dark"), scale = retinaScale, isSvg = isSvg, isDark = true)
    val d2 = ImageDescriptor(SuffixPathTransform("_dark@2x"), scale = retinaScale, isSvg = isSvg, isDark = true)
    val d3 = ImageDescriptor(SuffixPathTransform("_dark"), scale = nonRetinaScale, isSvg = isSvg, isDark = true)

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
    val d1 = ImageDescriptor(SuffixPathTransform("@2x"), scale = retinaScale, isSvg = isSvg)
    val d2 = ImageDescriptor(SuffixPathTransform(""), scale = nonRetinaScale, isSvg = isSvg)

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

/**
 * See [com.intellij.ui.icons.CustomIconUtilKt] and [loadIconCustomVersion] / [scaleIconOrLoadCustomVersion].
 */
@Internal
fun createImageDescriptorTransformListWithSizeSpecialization(
  path: String, isDark: Boolean, isStroke: Boolean, scaleContext: ScaleContext,
  originalWidth: Int, originalHeight: Int,
): List<ImageDescriptor> {
  val pixScale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
  val isSvg = path.endsWith(".svg")

  val sizeSpecializedDescriptors = ArrayList<ImageDescriptor>(2)
  if (isSvg) {
    val objScale = scaleContext.getScale(ScaleType.OBJ_SCALE).toFloat()
    val scaledWidth = (originalWidth * objScale).toInt()
    val scaledHeight = (originalHeight * objScale).toInt()

    if (isDark) {
      sizeSpecializedDescriptors += ImageDescriptor(SuffixPathTransform("@${scaledWidth}x${scaledHeight}_dark"), scale = pixScale / objScale, isSvg = true, isDark = true)
    }
    sizeSpecializedDescriptors += ImageDescriptor(SuffixPathTransform("@${scaledWidth}x${scaledHeight}"), scale = pixScale / objScale, isSvg = true, isDark = false)
  }

  val defaultDescriptors = createImageDescriptorList(path = path, isDark = isDark, isStroke = isStroke, pixScale = pixScale)
  return sizeSpecializedDescriptors + defaultDescriptors
}

@Internal
fun createCommonImagePathTransforms(
  path: String,
): List<(String, String) -> String> {
  val transforms = mutableListOf(
    SuffixPathTransform(""),
    SuffixPathTransform("_stroke"),
    SuffixPathTransform("@2x_dark"),
    SuffixPathTransform("_dark@2x"),
    SuffixPathTransform("_dark"),
    SuffixPathTransform("@2x"),
  )

  // todo: check available for the image?
  for (customSize in listOf(20)) {
    transforms += SuffixPathTransform("@${customSize}x${customSize}_dark")
    transforms += SuffixPathTransform("@${customSize}x${customSize}")
  }

  return transforms
}

internal class SuffixPathTransform(val suffix: String) : (String, String) -> String {
  override fun invoke(p: String, e: String): String = "${p}${suffix}.${e}"
}
