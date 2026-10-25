// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.platform.icons.design.Circle
import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.design.DisplayPoint
import com.intellij.platform.icons.design.IconAlign
import com.intellij.platform.icons.design.IconUnit
import com.intellij.platform.icons.design.Pixel
import com.intellij.platform.icons.design.Rectangle
import com.intellij.platform.icons.design.Shape
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.ScalingContext
import com.intellij.platform.icons.scale.FactorScale
import com.intellij.platform.icons.scale.FillAreaScale
import com.intellij.platform.icons.scale.FitAreaScale
import com.intellij.platform.icons.scale.IconScale
import javax.swing.Icon

/** Display helpers shared by the tree builder and the rendering snapshot. Never throw. */
internal object InspectorFormatting {
  private const val MAX_TEXT = 400

  fun unit(unit: IconUnit?): String = when (unit) {
    null -> "null"
    is Pixel -> "${unit.value}px"
    is DisplayPoint -> "${number(unit.value)}dp"
    else -> safeToString(unit)
  }

  fun scale(scale: IconScale?): String = when (scale) {
    null -> "none"
    is FactorScale -> "factor(${number(scale.factor)})"
    is FitAreaScale -> "fitArea(${unit(scale.width)} × ${unit(scale.height)}, relative=${scale.relative})"
    is FillAreaScale -> "fillArea(${unit(scale.width)} × ${unit(scale.height)}, relative=${scale.relative})"
    else -> safeToString(scale)
  }

  fun color(color: Color?): String {
    if (color == null) return "null"
    return try {
      color.toHex()
    }
    catch (_: Throwable) {
      safeToString(color)
    }
  }

  fun shape(shape: Shape?): String = when (shape) {
    null -> "null"
    is Circle -> "circle(radius=${unit(shape.radius)})"
    is Rectangle -> "rectangle(${unit(shape.width)} × ${unit(shape.height)})"
  }

  fun align(align: IconAlign?): String {
    if (align == null) return "null"
    return try {
      "${align.verticalAlign}/${align.horizontalAlign}"
    }
    catch (_: Throwable) {
      safeToString(align)
    }
  }

  fun dimensions(dimensions: Dimensions?): String = if (dimensions == null) "unknown" else size(dimensions.width, dimensions.height)

  fun size(width: Int, height: Int): String = "$width×$height"

  fun iconSize(icon: Icon): String = try {
    size(icon.iconWidth, icon.iconHeight)
  }
  catch (_: Throwable) {
    "unknown"
  }

  fun scalingSubtitle(scaling: ScalingContext, dimensions: Dimensions?): String =
    "${dimensions(dimensions)} @${number(scaling.displayDensity.toDouble())}" +
    (if (scaling.contextScale != 1f) " ×${number(scaling.contextScale.toDouble())}" else "")

  fun number(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return value.toString()
    val rounded = Math.round(value * 1000.0) / 1000.0
    return if (rounded == Math.rint(rounded) && kotlin.math.abs(rounded) < 1e9) rounded.toLong().toString() + ".0" else rounded.toString()
  }

  fun number(value: Float): String = number(value.toDouble())

  fun className(any: Any?): String = any?.javaClass?.name ?: "null"

  fun simpleClassName(any: Any?): String = any?.javaClass?.let(::simpleName) ?: "null"

  fun simpleName(clazz: Class<*>): String {
    val simple = clazz.simpleName
    if (simple.isNotEmpty()) return simple
    // anonymous / lambda classes
    return clazz.name.substringAfterLast('.')
  }

  fun identity(any: Any): String = "${simpleClassName(any)}@${Integer.toHexString(System.identityHashCode(any))}"

  fun safeToString(any: Any?): String {
    if (any == null) return "null"
    val text = try {
      any.toString()
    }
    catch (e: Throwable) {
      "<toString failed: ${e.javaClass.simpleName}>"
    }
    return truncate(text)
  }

  fun truncate(text: String, max: Int = MAX_TEXT): String = if (text.length <= max) text else text.take(max) + "… (${text.length} chars)"
}
