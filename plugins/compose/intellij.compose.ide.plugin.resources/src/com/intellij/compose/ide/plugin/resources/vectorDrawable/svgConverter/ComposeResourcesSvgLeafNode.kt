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

import com.intellij.compose.ide.plugin.resources.vectorDrawable.rendering.ParseMode
import com.intellij.compose.ide.plugin.resources.vectorDrawable.rendering.parsePathStringIntoNodes
import com.intellij.compose.ide.plugin.resources.vectorDrawable.svgConverter.ComposeResourcesSvgTree.Companion.formatFloatValue
import com.intellij.openapi.diagnostic.logger
import org.w3c.dom.Element
import java.awt.geom.AffineTransform
import java.io.OutputStreamWriter
import kotlin.math.abs
import kotlin.math.sqrt

/*
 * Based on [com.android.ide.common.vectordrawable.SvgLeafNode]
 * Java source: https://cs.android.com/android-studio/platform/tools/base/+/3012cec8bcc75944cbdc948a43abd35c5bd24d2d:sdk-common/src/main/java/com/android/ide/common/vectordrawable/SvgLeafNode.java
 */
internal class ComposeResourcesSvgLeafNode(
  svgTree: ComposeResourcesSvgTree,
  documentElement: Element,
  name: String?,
) : ComposeResourcesSvgNode(svgTree, documentElement, name) {

  var pathData: String? = null
  private var fillGradientNode: ComposeResourcesSvgGradientNode? = null
  private var strokeGradientNode: ComposeResourcesSvgGradientNode? = null

  override val isGroupNode = false

  val hasGradient: Boolean
    get() = fillGradientNode != null || strokeGradientNode != null

  override fun deepCopy(): ComposeResourcesSvgLeafNode {
    val newNode = ComposeResourcesSvgLeafNode(svgTree, documentElement, name)
    newNode.copyFrom(this)
    return newNode
  }

  override fun copyFrom(from: ComposeResourcesSvgNode) {
    super.copyFrom(from)
    if (from !is ComposeResourcesSvgLeafNode) return
    pathData = from.pathData
  }

  override fun dumpNode(indent: String) {
    LOG.debug("$indent${pathData ?: " null pathData "}${name ?: " null name "}")
  }

  override fun transformIfNeeded(finalTransform: AffineTransform) {
    val path = pathData
    if (path.isNullOrEmpty()) return

    val nodes = parsePathStringIntoNodes(path, ParseMode.SVG)
    stackedTransform.preConcatenate(finalTransform)

    if (!stackedTransform.isIdentity || hasRelMoveAfterClose(nodes)) transform(stackedTransform, nodes)

    pathData = nodeListToString(nodes, svgTree)
  }

  override fun flatten(transform: AffineTransform) {
    stackedTransform.setTransform(transform)
    stackedTransform.concatenate(localTransform)

    if (vdAttributesMap["vector-effect"] == "non-scaling-stroke") return
    if ((stackedTransform.type and AffineTransform.TYPE_MASK_SCALE) == 0) return

    val strokeWidth = vdAttributesMap["stroke-width"]?.toDoubleOrNull() ?: return
    val determinant = stackedTransform.determinant
    if (determinant != 0.0) vdAttributesMap["stroke-width"] = svgTree.formatCoordinate(strokeWidth * sqrt(abs(determinant)))

    if ((stackedTransform.type and AffineTransform.TYPE_GENERAL_SCALE) != 0) logWarning("Scaling of the stroke width is approximate")
  }

  override fun writeXml(writer: OutputStreamWriter, indent: String) {
    if (pathData.isNullOrEmpty()) return

    if (strokeBeforeFill) {
      writePathElementWithSuppressedFillOrStroke(writer, "fill", indent)
      writePathElementWithSuppressedFillOrStroke(writer, "stroke", indent)
    }
    else {
      writePathElement(writer, indent)
    }
  }

  private fun writePathElementWithSuppressedFillOrStroke(writer: OutputStreamWriter, attribute: String, indent: String) {
    val savedValue = vdAttributesMap.put(attribute, "#00000000")
    try {
      writePathElement(writer, indent)
    }
    finally {
      if (savedValue == null) vdAttributesMap.remove(attribute) else vdAttributesMap[attribute] = savedValue
    }
  }

  private fun writePathElement(writer: OutputStreamWriter, indent: String) {
    val fillColor = vdAttributesMap["fill"]
    val strokeColor = vdAttributesMap["stroke"]
    val emptyFill = fillColor == "none" || fillColor == "#00000000"
    val emptyStroke = strokeColor == null || strokeColor == "none"

    if (emptyFill && emptyStroke) return

    writer.write("$indent<path${System.lineSeparator()}")

    if (fillColor == null && fillGradientNode == null) {
      LOG.debug("Adding default fill color")
      writer.write("$indent${CONTINUATION_INDENT}android:fillColor=\"#FF000000\"${System.lineSeparator()}")
    }

    if (!emptyStroke && "stroke-width" !in vdAttributesMap && strokeGradientNode == null) {
      LOG.debug("Adding default stroke width")
      writer.write("$indent${CONTINUATION_INDENT}android:strokeWidth=\"1\"${System.lineSeparator()}")
    }

    writer.write("$indent${CONTINUATION_INDENT}android:pathData=\"$pathData\"")
    writeAttributeValues(writer, indent)

    val closing = if (hasGradient) ">" else "/>"
    writer.write("$closing${System.lineSeparator()}")

    fillGradientNode?.writeXml(writer, indent + INDENT_UNIT)
    strokeGradientNode?.writeXml(writer, indent + INDENT_UNIT)

    if (hasGradient) writer.write("$indent</path>${System.lineSeparator()}")
  }

  private fun writeAttributeValues(writer: OutputStreamWriter, indent: String) {
    parsePathOpacity()

    for ((name, value) in vdAttributesMap) {
      val attribute = PRESENTATION_MAP[name]
      if (attribute.isNullOrEmpty()) continue

      val svgValue = value.trim()
      var vdValue = colorSvg2Vd(svgValue, "#000000")

      if (vdValue == null) {
        if (name == "fill" || name == "stroke") {
          val gradientNode = getGradientNode(svgValue)
          if (gradientNode != null) {
            val copiedGradient = gradientNode.deepCopy().apply {
              svgLeafNode = this@ComposeResourcesSvgLeafNode
              gradientUsage = if (name == "fill") {
                ComposeResourcesSvgGradientNode.GradientUsage.FILL
              }
              else {
                ComposeResourcesSvgGradientNode.GradientUsage.STROKE
              }
            }
            if (name == "fill") fillGradientNode = copiedGradient else strokeGradientNode = copiedGradient
            continue
          }
        }
        vdValue = svgValue.removeSuffix("px").trim()
      }

      writer.write("${System.lineSeparator()}$indent$CONTINUATION_INDENT$attribute=\"$vdValue\"")
    }
  }

  private fun getGradientNode(svgValue: String): ComposeResourcesSvgGradientNode? {
    if (!svgValue.startsWith("url(#") || !svgValue.endsWith(")")) return null
    val id = svgValue.removePrefix("url(#").removeSuffix(")")
    return svgTree.getSvgNodeFromId(id) as? ComposeResourcesSvgGradientNode
  }

  private fun parsePathOpacity() {
    val opacity = getOpacityValueFromMap("opacity")
    val fillOpacity = getOpacityValueFromMap("fill-opacity")
    val strokeOpacity = getOpacityValueFromMap("stroke-opacity")

    putOpacityValueToMap("fill-opacity", fillOpacity * opacity)
    putOpacityValueToMap("stroke-opacity", strokeOpacity * opacity)
    vdAttributesMap.remove("opacity")
  }

  private fun getOpacityValueFromMap(attributeName: String): Double {
    val opacity = vdAttributesMap[attributeName] ?: return 1.0
    val value = try {
      if (opacity.endsWith("%")) opacity.dropLast(1).toDouble() / 100.0 else opacity.toDouble()
    }
    catch (_: NumberFormatException) {
      return 1.0
    }
    return value.coerceIn(0.0, 1.0)
  }

  private fun putOpacityValueToMap(attributeName: String, opacity: Double) {
    val attributeValue = formatFloatValue(opacity)
    if (attributeValue == "1") vdAttributesMap.remove(attributeName) else vdAttributesMap[attributeName] = attributeValue
  }

  companion object {
    private val LOG = logger<ComposeResourcesSvgLeafNode>()

    private val PRESENTATION_MAP = mapOf(
      "fill" to "android:fillColor",
      "stroke" to "android:strokeColor",
      "stroke-width" to "android:strokeWidth",
      "stroke-linecap" to "android:strokeLineCap",
      "stroke-linejoin" to "android:strokeLineJoin",
      "stroke-miterlimit" to "android:strokeMiterLimit",
      "stroke-opacity" to "android:strokeAlpha",
      "fill-opacity" to "android:fillAlpha",
      "fill-rule" to "android:fillType",
      "stroke-dasharray" to "android:strokeDashArray",
      "stroke-dashoffset" to "android:strokeDashOffset"
    )
  }
}
