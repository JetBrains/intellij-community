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

import com.intellij.compose.ide.plugin.resources.vectorDrawable.svgConverter.ComposeResourcesSvgConverter.Companion.presentationMap
import com.intellij.openapi.diagnostic.logger
import org.w3c.dom.Element
import java.awt.geom.AffineTransform
import java.io.OutputStreamWriter
import kotlin.math.tan

/*
 * Based on [com.android.ide.common.vectordrawable.SvgNode]
 * Java source: https://cs.android.com/android-studio/platform/tools/base/+/3012cec8bcc75944cbdc948a43abd35c5bd24d2d:sdk-common/src/main/java/com/android/ide/common/vectordrawable/SvgNode.java
 */
internal abstract class ComposeResourcesSvgNode(
  val svgTree: ComposeResourcesSvgTree,
  val documentElement: Element,
  val name: String?,
) {
  val vdAttributesMap = mutableMapOf<String, String>()
  var strokeBeforeFill = false
  var localTransform = AffineTransform()
  var stackedTransform = AffineTransform()

  init {
    val attrs = documentElement.attributes
    for (i in 0 until attrs.length) {
      val node = attrs.item(i)
      val nodeName = node.nodeName ?: continue
      val nodeValue = node.nodeValue ?: continue

      if (nodeName in presentationMap) fillPresentationAttributesInternal(nodeName, nodeValue)

      if (nodeName == "transform") {
        LOG.debug("$nodeName $nodeValue")
        parseLocalTransform(nodeValue)
      }
    }
  }

  protected fun parseLocalTransform(nodeValue: String) {
    val matrices = nodeValue.replace(',', ' ').split(TRANSFORM_SPLIT_REGEX).filter { it.isNotEmpty() }

    matrices.chunked(2).forEach { chunk ->
      if (chunk.size == 2) parseOneTransform(chunk[0].trim(), chunk[1].trim())?.let { localTransform.concatenate(it) }

    }
  }

  private fun parseOneTransform(type: String, data: String): AffineTransform? {
    val numbers = parseNumbers(data) ?: return null
    val transform = AffineTransform()

    when (type.lowercase()) {
      "matrix" -> {
        if (numbers.size != 6) return null
        transform.setTransform(
          numbers[0].toDouble(), numbers[1].toDouble(),
          numbers[2].toDouble(), numbers[3].toDouble(),
          numbers[4].toDouble(), numbers[5].toDouble()
        )
      }
      "translate" -> {
        if (numbers.size !in 1..2) return null
        transform.translate(numbers[0].toDouble(), numbers.getOrNull(1)?.toDouble() ?: 0.0)
      }
      "scale" -> {
        if (numbers.size !in 1..2) return null
        val sx = numbers[0].toDouble()
        transform.scale(sx, numbers.getOrNull(1)?.toDouble() ?: sx)
      }
      "rotate" -> {
        if (numbers.size != 1 && numbers.size != 3) return null
        val angle = Math.toRadians(numbers[0].toDouble())
        transform.rotate(angle, numbers.getOrNull(1)?.toDouble() ?: 0.0, numbers.getOrNull(2)?.toDouble() ?: 0.0)
      }
      "skewx" -> {
        if (numbers.size != 1) return null
        transform.shear(tan(Math.toRadians(numbers[0].toDouble())), 0.0)
      }
      "skewy" -> {
        if (numbers.size != 1) return null
        transform.shear(0.0, tan(Math.toRadians(numbers[0].toDouble())))
      }
      else -> return null
    }
    return transform
  }

  private fun parseNumbers(data: String): FloatArray? {
    val parts = data.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null

    val numbers = parts.mapNotNull { it.toFloatOrNull() }
    return if (numbers.size == parts.size) numbers.toFloatArray() else null
  }

  /** Dumps the current node's debug info. */
  abstract fun dumpNode(indent: String)

  /** Writes content of the node into the VectorDrawable's XML file. */
  abstract fun writeXml(writer: OutputStreamWriter, indent: String)

  /** Calls the [Visitor.visit] method for this node and its descendants. */
  open fun accept(visitor: Visitor): VisitResult = visitor.visit(this)

  /** Returns true if the node is a group node. */
  abstract val isGroupNode: Boolean

  /** Transforms the current node with the transformation matrix. */
  abstract fun transformIfNeeded(finalTransform: AffineTransform)

  abstract fun flatten(transform: AffineTransform)

  /** Checks the validity of the node and logs any issues associated with it. */
  open fun validate() {}

  abstract fun deepCopy(): ComposeResourcesSvgNode

  open fun copyFrom(from: ComposeResourcesSvgNode) {
    fillEmptyAttributes(from.vdAttributesMap)
    localTransform = from.localTransform.clone() as AffineTransform
  }

  open fun fillPresentationAttributes(name: String, value: String) {
    fillPresentationAttributesInternal(name, value)
  }

  private fun fillPresentationAttributesInternal(name: String, value: String) {
    if (name == "paint-order") {
      val order = value.split(WHITESPACE_REGEX)
      strokeBeforeFill = order.indexOf("stroke") in 0..<order.indexOf("fill")
      return
    }

    val processedValue = when (name) {
      "fill-rule", "clip-rule" -> when (value) {
        "nonzero" -> "nonZero"
        "evenodd" -> "evenOdd"
        else -> value
      }
      "stroke-width" -> {
        if (value == "0") vdAttributesMap.remove("stroke")
        value
      }
      else -> value
    }

    LOG.debug(">>>> PROP $name = $processedValue")

    if (processedValue.startsWith("url(") && name != "fill" && name != "stroke") {
      logError("Unsupported URL value: $processedValue")
      return
    }

    if (processedValue.isNotEmpty()) {
      vdAttributesMap[name] = processedValue
    }
  }

  fun fillEmptyAttributes(parentAttributesMap: Map<String, String>) {
    parentAttributesMap.forEach { (name, value) -> vdAttributesMap.putIfAbsent(name, value) }
  }

  /** Returns the value of the given attribute, or an empty string if not present. */
  fun getAttributeValue(attribute: String): String {
    return documentElement.getAttribute(attribute) ?: ""
  }

  /** Converts an SVG color value to "#RRGGBB" or "#RGB" format. */
  fun colorSvg2Vd(svgColor: String, errorFallbackColor: String): String? {
    return try {
      colorSvg2Vd(svgColor)
    }
    catch (_: IllegalArgumentException) {
      logError("Unsupported color format \"$svgColor\"")
      errorFallbackColor
    }
  }

  /** Returns the id referenced by 'href' or 'xlink:href' attribute. */
  fun getHrefId(): String {
    val value = documentElement.getAttribute("href").ifEmpty { documentElement.getAttribute("xlink:href") }
    return value.removePrefix("#")
  }

  protected fun logError(message: String) = svgTree.logError(message, documentElement)

  protected fun logWarning(message: String) = svgTree.logWarning(message, documentElement)

  fun interface Visitor {
    fun visit(node: ComposeResourcesSvgNode): VisitResult
  }

  enum class VisitResult { CONTINUE, SKIP_CHILDREN, ABORT }

  enum class ClipRule { NON_ZERO, EVEN_ODD }

  companion object {
    private val LOG = logger<ComposeResourcesSvgNode>()

    const val INDENT_UNIT = "  "
    const val CONTINUATION_INDENT = INDENT_UNIT + INDENT_UNIT

    private val TRANSFORM_SPLIT_REGEX = "[()]".toRegex()
    private val WHITESPACE_REGEX = "\\s+".toRegex()
  }
}