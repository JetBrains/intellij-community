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

import com.intellij.compose.ide.plugin.resources.vectorDrawable.svgConverter.ComposeResourcesSvgNode.Companion.CONTINUATION_INDENT
import com.intellij.compose.ide.plugin.resources.vectorDrawable.svgConverter.ComposeResourcesSvgNode.Companion.INDENT_UNIT
import com.intellij.compose.ide.plugin.resources.vectorDrawable.svgConverter.ComposeResourcesXmlParser.getStartLine
import com.intellij.openapi.diagnostic.logger
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.awt.geom.AffineTransform
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max

/**
 * Based on [com.android.ide.common.vectordrawable.SvgTree]
 * Java source: https://cs.android.com/android-studio/platform/tools/base/+/3012cec8bcc75944cbdc948a43abd35c5bd24d2d:sdk-common/src/main/java/com/android/ide/common/vectordrawable/SvgTree.java
 *
 * Key differences:
 * - Uses [use] in [writeXml] for stream management
 * - Uses [compareValuesBy] for sorting
 * - [LogMessage] is a data class
 * - Uses local `formatFloatValue` instead of `XmlUtils.formatFloatValue`
 */
internal class ComposeResourcesSvgTree {
  var root: ComposeResourcesSvgGroupNode? = null
  var hasLeafNode = false
  var hasGradient = false

  var width = -1f
    private set
  var height = -1f
    private set
  var viewBox: FloatArray? = null
    private set

  private val rootTransform = AffineTransform()
  private var fileName: String? = null
  private val logMessages = mutableListOf<LogMessage>()
  private var coordinateFormat: NumberFormat? = null

  private val idMap = hashMapOf<String, ComposeResourcesSvgNode>()
  private val ignoredIds = hashSetOf<String>()
  private val pendingUseGroupSet = hashSetOf<ComposeResourcesSvgGroupNode>()
  private val pendingGradientRefSet = hashSetOf<ComposeResourcesSvgGradientNode>()
  private val clipPathAffectedNodes = linkedMapOf<ComposeResourcesSvgNode, Pair<ComposeResourcesSvgGroupNode, String>>()
  private val styleAffectedNodes = mutableMapOf<String, MutableSet<ComposeResourcesSvgNode>>()
  private val styleClassAttributeMap = mutableMapOf<String, String>()

  fun flatten() = root?.flatten(AffineTransform())

  fun validate() {
    root?.validate()
    if (logMessages.isEmpty() && !hasLeafNode) logError("No vector content found", null)
  }

  fun parse(file: Path, parseErrors: MutableList<String?>): Document? {
    fileName = file.fileName.toString()
    return ComposeResourcesXmlParser.parse(file, parseErrors)
  }

  fun normalize() {
    rootTransform.preConcatenate(AffineTransform(1f, 0f, 0f, 1f, -viewBox!![0], -viewBox!![1]))
    transform(rootTransform)

    LOG.info("matrix= $rootTransform")
  }

  enum class SvgLogLevel { ERROR, WARNING }

  fun dump() {
    LOG.info("file: $fileName")
    root?.dumpNode("")
  }

  fun logError(s: String?, node: Node?) = logErrorLine(s, node, SvgLogLevel.ERROR)

  fun logWarning(s: String?, node: Node?) = logErrorLine(s, node, SvgLogLevel.WARNING)

  fun logErrorLine(s: String?, node: Node?, level: SvgLogLevel) {
    require(!s.isNullOrEmpty()) { "Log message cannot be null or empty" }
    logMessages.add(LogMessage(level, node?.let { getStartLine(it) } ?: 0, s))
  }

  fun getErrorMessage(): String {
    if (logMessages.isEmpty()) return ""
    return logMessages.sorted().joinToString("\n") { it.formattedMessage }
  }

  fun getViewportWidth(): Float = viewBox?.get(2) ?: -1f

  fun getViewportHeight(): Float = viewBox?.get(3) ?: -1f

  private enum class SizeType { PIXEL, PERCENTAGE }

  fun parseDimension(node: Node) {
    val attrs = node.attributes
    var widthType = SizeType.PIXEL
    var heightType = SizeType.PIXEL

    for (i in 0 until attrs.length) {
      val attr = attrs.item(i)
      val name = attr.nodeName.trim()
      val value = attr.nodeValue.trim()

      when (name) {
        "width" -> {
          val (numericValue, sizeType) = parseValueWithUnit(value)
          width = numericValue
          widthType = sizeType
        }
        "height" -> {
          val (numericValue, sizeType) = parseValueWithUnit(value)
          height = numericValue
          heightType = sizeType
        }
        "viewBox" -> {
          val parts = value.split("\\s+".toRegex())
          viewBox = FloatArray(4) { j -> parts[j].toFloat() }
        }
      }
    }

    if (viewBox == null && width > 0 && height > 0) {
      viewBox = floatArrayOf(0f, 0f, width, height)
    }
    else if ((width < 0 || height < 0) && viewBox != null) {
      width = viewBox!![2]
      height = viewBox!![3]
    }

    if (widthType == SizeType.PERCENTAGE && width > 0) width = viewBox!![2] * width / 100
    if (heightType == SizeType.PERCENTAGE && height > 0) height = viewBox!![3] * height / 100
  }

  fun parseXValue(value: String) = parseCoordinateOrLength(value, getViewportWidth().toDouble())

  fun parseYValue(value: String) = parseCoordinateOrLength(value, getViewportHeight().toDouble())

  fun addIdToMap(id: String, svgNode: ComposeResourcesSvgNode) {
    idMap[id] = svgNode
  }

  fun getSvgNodeFromId(id: String) = idMap[id]

  fun addToPendingUseSet(useGroup: ComposeResourcesSvgGroupNode) {
    pendingUseGroupSet.add(useGroup)
  }

  fun getPendingUseSet() = pendingUseGroupSet

  fun addToPendingGradientRefSet(node: ComposeResourcesSvgGradientNode) {
    pendingGradientRefSet.add(node)
  }

  fun getPendingGradientRefSet() = pendingGradientRefSet

  fun addIgnoredId(id: String) {
    ignoredIds.add(id)
  }

  fun isIdIgnored(id: String) = id in ignoredIds

  fun addClipPathAffectedNode(child: ComposeResourcesSvgNode, currentGroup: ComposeResourcesSvgGroupNode, clipPathName: String) {
    clipPathAffectedNodes[child] = currentGroup to clipPathName
  }

  fun getClipPathAffectedNodesSet() = clipPathAffectedNodes.entries

  fun addAffectedNodeToStyleClass(className: String, child: ComposeResourcesSvgNode) {
    styleAffectedNodes.getOrPut(className) { mutableSetOf() }.add(child)
  }

  fun addStyleClassToTree(className: String, attributes: String) {
    styleClassAttributeMap[className] = attributes
  }

  fun getStyleClassAttr(classname: String) = styleClassAttributeMap[classname]

  fun getStyleAffectedNodes() = styleAffectedNodes.entries

  fun findParent(node: ComposeResourcesSvgNode) = root?.findParent(node)

  fun formatCoordinate(coordinate: Double): String = getCoordinateFormat().format(coordinate).trimInsignificantZeros()

  fun getCoordinateFormat(maxViewportSize: Float): NumberFormat {
    val effectiveSize = if (maxViewportSize > 0) maxViewportSize else 1f
    val exponent = floor(log10(effectiveSize.toDouble())).toInt()
    val fractionalDigits = (4 - exponent).coerceIn(0, 6)

    val pattern = if (fractionalDigits > 0) "#.${"#".repeat(fractionalDigits)}" else "#"
    return DecimalFormat(pattern, DecimalFormatSymbols(Locale.ROOT)).apply {
      roundingMode = RoundingMode.HALF_UP
    }
  }

  fun writeXml(stream: OutputStream) {
    checkNotNull(root) { "SvgTree is not fully initialized" }

    val nl = System.lineSeparator()
    OutputStreamWriter(stream, StandardCharsets.UTF_8).use { writer ->
      writer.write("$HEAD$nl")
      if (hasGradient) writer.write("$CONTINUATION_INDENT$AAPT_BOUND$nl")

      writer.write("${CONTINUATION_INDENT}android:width=\"${formatCoordinate(width.toDouble())}dp\"$nl")
      writer.write("${CONTINUATION_INDENT}android:height=\"${formatCoordinate(height.toDouble())}dp\"$nl")
      writer.write("${CONTINUATION_INDENT}android:viewportWidth=\"${formatCoordinate(getViewportWidth().toDouble())}\"$nl")
      writer.write("${CONTINUATION_INDENT}android:viewportHeight=\"${formatCoordinate(getViewportHeight().toDouble())}\">$nl")

      normalize()
      root?.writeXml(writer, INDENT_UNIT)
      writer.write("</vector>$nl")
    }
  }

  private fun transform(rootTransform: AffineTransform) = root?.transformIfNeeded(rootTransform)

  private fun parseValueWithUnit(value: String): Pair<Float, SizeType> {
    val unit = value.takeLast(2)
    return when {
      unit.matches(UNIT_REGEX) -> value.dropLast(2).toFloat() to SizeType.PIXEL
      value.endsWith("%") -> value.dropLast(1).toFloat() to SizeType.PERCENTAGE
      else -> value.toFloat() to SizeType.PIXEL
    }
  }

  private fun getCoordinateFormat(): NumberFormat {
    return coordinateFormat ?: getCoordinateFormat(max(getViewportHeight(), getViewportWidth())).also {
      coordinateFormat = it
    }
  }

  private fun parseCoordinateOrLength(value: String, percentageBase: Double): Double =
    if (value.endsWith("%")) value.dropLast(1).toDouble() / 100 * percentageBase else value.toDouble()

  private data class LogMessage(
    val level: SvgLogLevel,
    val line: Int,
    val message: String,
  ) : Comparable<LogMessage> {
    val formattedMessage: String
      get() = "${level.name}${if (line == 0) "" else " @ line $line"}: $message"

    override fun compareTo(other: LogMessage) = compareValuesBy(this, other, { it.level }, { it.line }, { it.message })
  }

  companion object {
    private val LOG = logger<ComposeResourcesSvgTree>()

    private const val HEAD = "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\""
    private const val AAPT_BOUND = "xmlns:aapt=\"http://schemas.android.com/aapt\""
    private val UNIT_REGEX = "em|ex|px|in|cm|mm|pt|pc".toRegex()

    internal fun String.trimInsignificantZeros(): String {
      if (!contains('.')) return this
      return trimEnd('0').trimEnd('.')
    }

    internal fun formatFloatValue(value: Double): String {
      require(value.isFinite()) { "Invalid number: $value" }
      return value.toFloat().toString().trimInsignificantZeros()
    }
  }
}
