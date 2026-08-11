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
package com.intellij.compose.ide.plugin.resources.vectorDrawable.svgConverter

import com.intellij.compose.ide.plugin.resources.vectorDrawable.rendering.ComposeResourcesVdElement.Companion.parseColorValue
import com.intellij.compose.ide.plugin.resources.vectorDrawable.rendering.ComposeResourcesVdPath.Companion.applyAlpha
import com.intellij.compose.ide.plugin.resources.vectorDrawable.rendering.ParseMode
import com.intellij.compose.ide.plugin.resources.vectorDrawable.rendering.applyNodesToPath
import com.intellij.compose.ide.plugin.resources.vectorDrawable.rendering.parsePathStringIntoNodes
import com.intellij.compose.ide.plugin.resources.vectorDrawable.svgConverter.ComposeResourcesSvgConverter.Companion.gradientMap
import com.intellij.compose.ide.plugin.resources.vectorDrawable.svgConverter.ComposeResourcesSvgTree.Companion.trimInsignificantZeros
import com.intellij.openapi.diagnostic.logger
import org.w3c.dom.Element
import java.awt.geom.AffineTransform
import java.awt.geom.NoninvertibleTransformException
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.io.OutputStreamWriter
import kotlin.math.max

/*
 * Based on [com.android.ide.common.vectordrawable.SvgGradientNode]
 * Java source: https://cs.android.com/android-studio/platform/tools/base/+/74a5c9785c3ad9f7f0ce8fbf4adfb2ae2c6bec2c:sdk-common/src/main/java/com/android/ide/common/vectordrawable/SvgGradientNode.java
 *
 * Key differences:
 * - Fixed a logical bug in [resolveHref]: The original Java code incorrectly checked [getPendingUseSet] for a gradient node
 * - Replaced sequential [writer.write] blocks with concise Kotlin string templates
 * */
internal class ComposeResourcesSvgGradientNode(
  svgTree: ComposeResourcesSvgTree,
  documentElement: Element,
  name: String?,
) : ComposeResourcesSvgNode(svgTree, documentElement, name) {

  private val gradientStops = mutableListOf<GradientStop>()
  private var boundingBox: Rectangle2D? = null

  var svgLeafNode: ComposeResourcesSvgLeafNode? = null
  var gradientUsage: GradientUsage? = null

  override val isGroupNode = false

  override fun dumpNode(indent: String) {
    LOG.debug("$indent current gradient is: $name")
  }

  override fun writeXml(writer: OutputStreamWriter, indent: String) {
    if (gradientStops.isEmpty()) {
      logError("Gradient has no stop info")
      return
    }

    val bounds = setBoundingBox()
    boundingBox = bounds
    var height = bounds.height
    var width = bounds.width
    var startX = bounds.x
    var startY = bounds.y

    val isUserSpaceOnUse = vdAttributesMap["gradientUnits"] == "userSpaceOnUse"

    if (isUserSpaceOnUse) {
      startX = 0.0
      startY = 0.0
      height = svgTree.height.toDouble()
      width = svgTree.width.toDouble()
    }

    if (width == 0.0 || height == 0.0) return

    val attrName = if (gradientUsage == GradientUsage.FILL) "android:fillColor" else "android:strokeColor"
    writer.write("$indent<aapt:attr name=\"$attrName\">${System.lineSeparator()}")
    writer.write("$indent$INDENT_UNIT<gradient")

    val transformValue = vdAttributesMap["gradientTransform"]
    if (!transformValue.isNullOrEmpty()) {
      parseLocalTransform(transformValue)
      if (!isUserSpaceOnUse) {
        val tr = AffineTransform(width, 0.0, 0.0, height, 0.0, 0.0)
        localTransform.preConcatenate(tr)
        try {
          tr.invert()
        }
        catch (_: NoninvertibleTransformException) {
          logError("Non-invertible gradient transform detected")
          return // Not going to happen because width * height != 0
        }
        localTransform.concatenate(tr)
      }
    }

    if (isUserSpaceOnUse) svgLeafNode?.stackedTransform?.let { localTransform.preConcatenate(it) }

    val gradientBounds: DoubleArray
    val transformedBounds: DoubleArray
    val gradientType = vdAttributesMap.getOrDefault("gradientType", "linear")

    if (gradientType == "linear") {
      gradientBounds = DoubleArray(4)
      transformedBounds = DoubleArray(4)
      for ((key, index) in VECTOR_COORDINATE_MAP) {
        val defaultValue = if (index == 2) 1.0 else 0.0
        val result = getGradientCoordinate(key, defaultValue)

        var coordValue = result.value
        if (!isUserSpaceOnUse || result.isPercentage) {
          coordValue = if (index % 2 == 0) coordValue * width + startX else coordValue * height + startY
        }
        gradientBounds[index] = coordValue
        transformedBounds[index] = coordValue

        if (!vdAttributesMap.containsKey(key)) vdAttributesMap[key] = ""
      }
      localTransform.transform(gradientBounds, 0, transformedBounds, 0, 2)
    }
    else {
      gradientBounds = DoubleArray(2)
      transformedBounds = DoubleArray(2)
      val cxResult = getGradientCoordinate("cx", .5)
      var cx = cxResult.value
      if (!isUserSpaceOnUse || cxResult.isPercentage) cx = width * cx + startX

      val cyResult = getGradientCoordinate("cy", .5)
      var cy = cyResult.value
      if (!isUserSpaceOnUse || cyResult.isPercentage) cy = height * cy + startY

      val rResult = getGradientCoordinate("r", .5)
      var r = rResult.value
      if (!isUserSpaceOnUse || rResult.isPercentage) r *= max(height, width)

      gradientBounds[0] = cx
      transformedBounds[0] = cx
      gradientBounds[1] = cy
      transformedBounds[1] = cy

      localTransform.transform(gradientBounds, 0, transformedBounds, 0, 1)
      val radius = Point2D.Double(r, 0.0)
      val transformedRadius = Point2D.Double(r, 0.0)
      localTransform.deltaTransform(radius, transformedRadius)

      vdAttributesMap["cx"] = svgTree.formatCoordinate(transformedBounds[0])
      vdAttributesMap["cy"] = svgTree.formatCoordinate(transformedBounds[1])
      vdAttributesMap["r"] = svgTree.formatCoordinate(transformedRadius.distance(0.0, 0.0))
    }

    for ((svgAttribute, gradientAttr) in gradientMap) {
      val svgValue = vdAttributesMap[svgAttribute]?.trim() ?: continue
      if (gradientAttr.isEmpty()) continue

      val vdValue = colorSvg2Vd(svgValue, "#000000") ?: run {
        val coordinateIndex = VECTOR_COORDINATE_MAP[svgAttribute]
        when {
          coordinateIndex != null -> svgTree.formatCoordinate(transformedBounds[coordinateIndex])
          svgAttribute == "spreadMethod" -> when (svgValue) {
            "pad" -> "clamp"
            "reflect" -> "mirror"
            "repeat" -> "repeat"
            else -> {
              logError("Unsupported spreadMethod $svgValue")
              "clamp"
            }
          }
          svgValue.endsWith("%") -> svgTree.formatCoordinate(getGradientCoordinate(svgAttribute, 0.0).value)
          else -> svgValue
        }
      }

      writer.write("${System.lineSeparator()}$indent$INDENT_UNIT$CONTINUATION_INDENT$gradientAttr=\"$vdValue\"")
    }
    writer.write(">${System.lineSeparator()}")

    writeGradientStops(writer, indent + INDENT_UNIT + INDENT_UNIT)
    writer.write("$indent$INDENT_UNIT</gradient>${System.lineSeparator()}")
    writer.write("$indent</aapt:attr>${System.lineSeparator()}")
  }

  override fun transformIfNeeded(finalTransform: AffineTransform) {
    // Transformation is done in the writeXml method.
  }

  override fun flatten(transform: AffineTransform) {
    stackedTransform.setTransform(transform)
    stackedTransform.concatenate(localTransform)
  }

  override fun deepCopy(): ComposeResourcesSvgGradientNode {
    val newInstance = ComposeResourcesSvgGradientNode(svgTree, documentElement, name)
    newInstance.copyFrom(this)
    return newInstance
  }

  override fun copyFrom(from: ComposeResourcesSvgNode) {
    super.copyFrom(from)
    if (gradientStops.isNotEmpty()) return
    if (from !is ComposeResourcesSvgGradientNode) return

    from.gradientStops.forEach { addGradientStop(it.color, it.offset, it.opacity) }
  }

  fun resolveHref(svgTree: ComposeResourcesSvgTree): Boolean {
    val id = getHrefId()
    val referencedNode = if (id.isEmpty()) null else svgTree.getSvgNodeFromId(id)
    when (referencedNode) {
      is ComposeResourcesSvgGradientNode -> {
        if (referencedNode in svgTree.getPendingGradientRefSet()) return false
        copyFrom(referencedNode)
      }
      null -> {
        if (id.isEmpty() || !svgTree.isIdIgnored(id)) {
          svgTree.logError("Referenced id not found", documentElement)
        }
      }
      else -> svgTree.logError("Referenced element is not a gradient", documentElement)
    }
    return true
  }

  fun addGradientStop(color: String, offset: String, opacity: String) {
    gradientStops.add(GradientStop(color, offset, opacity))
  }

  enum class GradientUsage { FILL, STROKE }

  internal class GradientStop(val color: String, val offset: String, var opacity: String = "")

  private fun writeGradientStops(writer: OutputStreamWriter, indent: String) {
    for (g in gradientStops) {
      var color = g.color
      val opacity = g.opacity.toFloatOrNull() ?: run {
        logWarning("Unsupported opacity value")
        1f
      }
      try {
        color = String.format("#%08X", applyAlpha(parseColorValue(color), opacity))
      }
      catch (_: IllegalArgumentException) {
        logWarning("Unsupported color value $color")
      }

      writer.write("$indent<item android:offset=\"${g.offset.trimInsignificantZeros()}\" android:color=\"$color\"/>${System.lineSeparator()}")

      if (gradientStops.size == 1) {
        logWarning("Gradient has only one color stop")
        writer.write("$indent<item android:offset=\"1\" android:color=\"$color\"/>${System.lineSeparator()}")
      }
    }
  }

  private fun getGradientCoordinate(key: String, defaultValue: Double): GradientCoordResult {
    val vdValue = vdAttributesMap[key]?.trim() ?: return GradientCoordResult(defaultValue, false)
    if (key == "r" && vdValue.startsWith("-")) return GradientCoordResult(defaultValue, false)

    return try {
      if (vdValue.endsWith("%")) GradientCoordResult(vdValue.removeSuffix("%").toDouble() / 100, true)
      else GradientCoordResult(vdValue.toDouble(), false)
    }
    catch (_: NumberFormatException) {
      logError("Unsupported coordinate value")
      GradientCoordResult(defaultValue, false)
    }
  }

  private fun setBoundingBox(): Rectangle2D {
    val svgPath = Path2D.Double()
    val nodes = parsePathStringIntoNodes(svgLeafNode?.pathData ?: "", ParseMode.SVG)
    applyNodesToPath(nodes, svgPath)
    return svgPath.bounds2D
  }

  private data class GradientCoordResult(val value: Double, val isPercentage: Boolean)

  companion object {
    private val LOG = logger<ComposeResourcesSvgGradientNode>()
    private val VECTOR_COORDINATE_MAP = mapOf("x1" to 0, "y1" to 1, "x2" to 2, "y2" to 3)
  }
}
