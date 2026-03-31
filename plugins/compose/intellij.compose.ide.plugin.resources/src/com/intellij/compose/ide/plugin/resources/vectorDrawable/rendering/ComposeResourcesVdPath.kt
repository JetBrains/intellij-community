/*
 * Copyright (C) 2015 The Android Open Source Project
 * Modified 2026 by JetBrains s.r.o.
 * Copyright (C) 2026 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.resources.vectorDrawable.rendering

import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.JBColor
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node as DomNode
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.MultipleGradientPaint
import java.awt.MultipleGradientPaint.CycleMethod
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.geom.Point2D

/**
 * Based on [com.android.ide.common.vectordrawable.VdPath]
 * Java source: https://cs.android.com/android-studio/platform/tools/base/+/719cd9aba22ed37b7e1ace12ca545d369030f355:sdk-common/src/main/java/com/android/ide/common/vectordrawable/VdPath.java
 *
 * Key differences from Android original:
 * - Uses [JBColor] instead of `java.awt.Color` for theme-aware colors
 * - Uses IntelliJ platform logger instead of `java.util.logging.Logger`
 * - [VectorDrawableGradient] is an inner class (Android has separate `VdGradient` file)
 */
internal class ComposeResourcesVdPath : ComposeResourcesVdElement() {
  override val isGroup: Boolean = false

  private var fillGradient: VectorDrawableGradient? = null
  private var strokeGradient: VectorDrawableGradient? = null
  private var nodeList: Array<Node> = emptyArray()

  private var strokeColor = 0
  private var fillColor = 0
  private var strokeWidth = 0f

  private var strokeLineCap = BasicStroke.CAP_BUTT
  private var strokeLineJoin = BasicStroke.JOIN_MITER
  private var strokeMiterLimit = 4.0f

  private var strokeAlpha = 1.0f
  private var fillAlpha = 1.0f
  private var fillType = Path2D.WIND_NON_ZERO

  private var trimPathStart = 0f
  private var trimPathEnd = 1.0f
  private var trimPathOffset = 0f

  override fun draw(g: Graphics2D, currentMatrix: AffineTransform, scaleX: Float, scaleY: Float) {
    val path2d = Path2D.Double(fillType)
    toPath(path2d)

    g.transform = AffineTransform()
    g.scale(scaleX.toDouble(), scaleY.toDouble())
    g.transform(currentMatrix)

    if (isClipPath) {
      g.clip(path2d)
      return
    }

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    if (fillColor != 0 && fillGradient == null) {
      val finalFillColor = applyAlpha(fillColor, fillAlpha)
      g.color = JBColor(finalFillColor, finalFillColor)
      g.fill(path2d)
    }

    if (strokeColor != 0 && strokeWidth != 0.0f && strokeGradient == null) {
      g.stroke = BasicStroke(strokeWidth, strokeLineCap, strokeLineJoin, strokeMiterLimit)

      val finalStrokeColor = applyAlpha(strokeColor, strokeAlpha)
      g.color = JBColor(finalStrokeColor, finalStrokeColor)
      g.draw(path2d)
    }

    fillGradient?.drawGradient(g, path2d, currentMatrix, fill = true)
    strokeGradient?.drawGradient(g, path2d, currentMatrix, fill = false)
  }

  override fun parseAttributes(attributes: NamedNodeMap) {
    for (i in 0 until attributes.length) {
      val attribute = attributes.item(i)
      if (attribute.namespaceURI != "http://schemas.android.com/tools") {
        setNameValue(attribute.nodeName, attribute.nodeValue)
      }
    }
  }

  override fun toString(): String =
    "Path: Name: $name Node: ${nodeList.contentToString()} fillColor: ${fillColor.toString(16)} " +
    "fillAlpha: $fillAlpha fillType: $fillType strokeColor: ${strokeColor.toString(16)} " +
    "strokeWidth: $strokeWidth strokeAlpha: $strokeAlpha"

  fun addGradientIfExists(current: DomNode) {
    val gradientNode = current.firstChild?.nextSibling ?: return

    val aaptAttrNode = gradientNode.attributes?.getNamedItem("name")
                       ?: throw RuntimeException("gradient resource not declared as an inline resource in the vector drawable.\n" +
                                                 "Recommended Action: Surround the gradient tag with " +
                                                 "<aapt:attr name=[attribute, such as \"android:fillcolor\"]> </aapt:attr>\n" +
                                                 "More Information: https://developer.android.com/guide/topics/resources/complex-xml-resources")

    val newGradient = VectorDrawableGradient()
    when (aaptAttrNode.nodeValue) {
      "android:fillColor" -> fillGradient = newGradient
      "android:strokeColor" -> strokeGradient = newGradient
    }

    val innerGradientNode = gradientNode.firstChild?.nextSibling ?: return
    if (innerGradientNode.nodeName != "gradient") return

    val gradientAttributes = innerGradientNode.attributes
    for (i in 0 until gradientAttributes.length) {
      val attr = gradientAttributes.item(i)
      newGradient.setGradientValue(attr.nodeName, attr.nodeValue)
    }

    val items = innerGradientNode.childNodes
    for (i in 0 until items.length) {
      val stop = items.item(i)
      if (stop.nodeName != "item") continue

      val colorAttr = stop.attributes.getNamedItem("android:color")?.nodeValue
      val offsetAttr = stop.attributes.getNamedItem("android:offset")?.nodeValue

      val color = colorAttr?.takeUnless { it.startsWith("@") || it.startsWith("?") } ?: "#000000"
      val offset = offsetAttr ?: "0".also { LOG.warn("Gradient stop missing 'android:offset'. Falling back to 0.") }
      newGradient.addStop(GradientStop(color, offset))
    }
  }

  private fun toPath(path: Path2D) {
    path.reset()
    if (nodeList.isNotEmpty()) {
      applyNodesToPath(nodeList, path)
    }
  }

  private fun setNameValue(name: String?, value: String) {
    var v = value
    if (v.startsWith("@") && name == "android:fillColor") {
      v = "#000000"
    }

    if (v.startsWith("@")) {
      throw RuntimeException("Cannot process attribute ${name ?: "unknown"}=\"$v\"")
    }

    when (name) {
      "android:pathData" -> nodeList = parsePathStringIntoNodes(v, ParseMode.ANDROID)
      "android:name" -> this.name = v
      "android:fillColor" -> fillColor = parseColorValue(v)
      "android:fillType" -> fillType = if (v.equals("evenOdd", ignoreCase = true)) Path2D.WIND_EVEN_ODD else Path2D.WIND_NON_ZERO
      "android:strokeColor" -> strokeColor = parseColorValue(v)
      "android:fillAlpha" -> fillAlpha = v.toFloat()
      "android:strokeAlpha" -> strokeAlpha = v.toFloat()
      "android:strokeWidth" -> strokeWidth = v.toFloat()
      "android:trimPathStart" -> trimPathStart = v.toFloat()
      "android:trimPathEnd" -> trimPathEnd = v.toFloat()
      "android:trimPathOffset" -> trimPathOffset = v.toFloat()
      "android:strokeLineCap" -> strokeLineCap = when (v) {
        "round" -> BasicStroke.CAP_ROUND
        "square" -> BasicStroke.CAP_SQUARE
        else -> BasicStroke.CAP_BUTT
      }
      "android:strokeLineJoin" -> strokeLineJoin = when (v) {
        "round" -> BasicStroke.JOIN_ROUND
        "bevel" -> BasicStroke.JOIN_BEVEL
        else -> BasicStroke.JOIN_MITER
      }
      "android:strokeMiterLimit" -> strokeMiterLimit = v.toFloat()
      else -> LOG.warn("Ignoring unrecognized path attribute: '$name'")
    }
  }

  companion object {
    private val LOG = logger<ComposeResourcesVdPath>()

    private fun applyAlpha(color: Int, alpha: Float): Int {
      val alphaBytes = color shr 24 and 0xFF
      return (color and 0x00FFFFFF) or ((alphaBytes * alpha).toInt() shl 24)
    }
  }

  class Node(var type: Char, var params: FloatArray) {
    constructor(n: Node) : this(n.type, n.params.copyOf())

    override fun toString(): String = buildString {
      append(type)
      params.forEachIndexed { index, param ->
        append(if (index % 2 == 0) ' ' else ',')
        append(param)
      }
    }
  }

  private data class GradientStop(val color: String, val offset: String)

  private inner class VectorDrawableGradient {
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var gradientRadius = 0f
    private var tileMode = "NO_CYCLE"
    private var gradientType = "linear"
    private val gradientStops = mutableListOf<GradientStop>()

    fun addStop(stop: GradientStop) =
      gradientStops.add(stop)

    fun setGradientValue(name: String, value: String) {
      when (name) {
        "android:type" -> gradientType = value
        "android:tileMode" -> tileMode = value
        "android:startX" -> startX = value.toFloat()
        "android:startY" -> startY = value.toFloat()
        "android:endX" -> endX = value.toFloat()
        "android:endY" -> endY = value.toFloat()
        "android:centerX" -> centerX = value.toFloat()
        "android:centerY" -> centerY = value.toFloat()
        "android:gradientRadius" -> gradientRadius = value.toFloat()
      }
    }

    fun drawGradient(g: Graphics2D, path2d: Path2D, currentMatrix: AffineTransform?, fill: Boolean) {
      if (gradientStops.isEmpty()) return

      val fractions = FloatArray(gradientStops.size) { gradientStops[it].offset.toFloat() }
      val colors = Array(gradientStops.size) {
        val parsedColor = parseColorValue(gradientStops[it].color)
        JBColor(parsedColor, parsedColor)
      }

      for (i in 0 until gradientStops.size - 1) {
        if (fractions[i] >= fractions[i + 1] && fractions[i] + 1.0E-6f <= 1.0f) {
          fractions[i + 1] = fractions[i] + 1.0E-6f
        }
      }

      var i = gradientStops.size - 2
      while (i >= 0 && fractions[i] >= fractions[i + 1] && fractions[i] >= 1.0f) {
        fractions[i] = fractions[i + 1] - 1.0E-6f
        --i
      }

      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      val stroke = BasicStroke(strokeWidth, strokeLineCap, strokeLineJoin, strokeMiterLimit)

      if (gradientStops.size == 1) {
        g.color = colors[0]
        if (!fill) g.stroke = stroke
        g.draw(path2d)
        return
      }

      val tile = when (tileMode) {
        "mirror" -> CycleMethod.REFLECT
        "repeat" -> CycleMethod.REPEAT
        else -> CycleMethod.NO_CYCLE
      }

      val transform = currentMatrix ?: AffineTransform()

      g.paint = when (gradientType) {
        "linear" -> LinearGradientPaint(
          Point2D.Float(startX, startY),
          Point2D.Float(endX, endY),
          fractions,
          colors,
          tile,
          MultipleGradientPaint.ColorSpaceType.SRGB,
          transform
        )
        "radial" -> RadialGradientPaint(
          Point2D.Float(centerX, centerY),
          gradientRadius,
          Point2D.Float(centerX, centerY),
          fractions,
          colors,
          tile,
          MultipleGradientPaint.ColorSpaceType.SRGB,
          transform
        )
        "sweep" -> {
          LOG.warn("Sweep gradients are not supported. Falling back to a solid color.")
          colors[0]
        }
        else -> {
          LOG.warn("Ignoring unsupported gradient type: '$gradientType'")
          return
        }
      }

      if (fill) g.fill(path2d)
      else {
        g.stroke = stroke
        g.draw(path2d)
      }
    }
  }
}