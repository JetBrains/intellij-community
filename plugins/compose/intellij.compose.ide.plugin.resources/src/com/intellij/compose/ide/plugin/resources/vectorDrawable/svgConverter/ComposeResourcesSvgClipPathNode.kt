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
import org.w3c.dom.Element
import java.awt.geom.AffineTransform
import java.io.OutputStreamWriter
import java.util.EnumMap

/**
 * Based on [com.android.ide.common.vectordrawable.SvgClipPathNode]
 * Java source: https://cs.android.com/android-studio/platform/tools/base/+/7406bf062c551d16620f84cd4b9a5f12a5043cdf:sdk-common/src/main/java/com/android/ide/common/vectordrawable/SvgClipPathNode.java
 */
internal class ComposeResourcesSvgClipPathNode(
  svgTree: ComposeResourcesSvgTree,
  documentElement: Element,
  name: String?,
) : ComposeResourcesSvgGroupNode(svgTree, documentElement, name) {

  private val affectedNodes = mutableListOf<ComposeResourcesSvgNode>()

  override fun deepCopy(): ComposeResourcesSvgClipPathNode {
    val newInstance = ComposeResourcesSvgClipPathNode(svgTree, documentElement, name)
    newInstance.copyFrom(this)
    return newInstance
  }

  override fun copyFrom(from: ComposeResourcesSvgNode) {
    super.copyFrom(from)
    if (from !is ComposeResourcesSvgClipPathNode) return
    from.affectedNodes.forEach { addAffectedNode(it) }
  }

  override fun addChild(child: ComposeResourcesSvgNode) {
    children.add(child)
    child.fillEmptyAttributes(vdAttributesMap)
  }

  fun addAffectedNode(child: ComposeResourcesSvgNode) {
    affectedNodes.add(child)
    child.fillEmptyAttributes(vdAttributesMap)
  }

  override fun flatten(transform: AffineTransform) {
    children.forEach {
      stackedTransform.setTransform(transform)
      stackedTransform.concatenate(localTransform)
      it.flatten(stackedTransform)
    }

    stackedTransform.setTransform(transform)
    affectedNodes.forEach { it.flatten(stackedTransform) }
    stackedTransform.concatenate(localTransform)

    if ("stroke-width" !in vdAttributesMap) return
    if ((stackedTransform.type and AffineTransform.TYPE_MASK_SCALE) == 0) return

    logWarning("Scaling of the stroke width is ignored")
  }

  override fun validate() {
    super.validate()
    children.forEach { it.validate() }
    affectedNodes.forEach { it.validate() }

    if (documentElement.tagName != "mask") return
    if (isWhiteFill()) return
    logError("Semitransparent mask cannot be represented by a vector drawable")
  }

  private fun isWhiteFill(): Boolean {
    var fillColor = vdAttributesMap["fill"] ?: return false
    fillColor = colorSvg2Vd(fillColor, "#000") ?: return false
    return parseColorValue(fillColor) == -0x1
  }

  override fun transformIfNeeded(finalTransform: AffineTransform) {
    (children + affectedNodes).forEach { it.transformIfNeeded(finalTransform) }
  }

  override fun writeXml(writer: OutputStreamWriter, indent: String) {
    val clipPathsByRule = collectClipPaths()
    if (clipPathsByRule.isEmpty()) return

    writer.writeGroupStart(indent)
    writeGroupContents(writer, clipPathsByRule, indent + INDENT_UNIT)
    writer.writeGroupEnd(indent)
  }

  fun setClipPathNodeAttributes() {
    affectedNodes.forEach { localTransform.concatenate(it.localTransform) }
  }

  private fun collectClipPaths(): Map<ClipRule, List<String>> {
    val clipPathsByRule = EnumMap<ClipRule, MutableList<String>>(ClipRule::class.java)
    val clipPathCollector = Visitor { node ->
      val pathData = (node as? ComposeResourcesSvgLeafNode)?.pathData
      if (pathData.isNullOrEmpty()) return@Visitor VisitResult.CONTINUE

      val clipRule =
        if (node.vdAttributesMap["clip-rule"] == "evenOdd") ClipRule.EVEN_ODD
        else ClipRule.NON_ZERO

      clipPathsByRule.getOrPut(clipRule) { mutableListOf() }.add(pathData)
      VisitResult.CONTINUE
    }
    children.forEach { it.accept(clipPathCollector) }
    return clipPathsByRule
  }

  private fun writeGroupContents(writer: OutputStreamWriter, clipPathsByRule: Map<ClipRule, List<String>>, indent: String) {
    val sortedRules = clipPathsByRule.keys.sortedBy { it != ClipRule.NON_ZERO }
    for (clipRule in sortedRules) {
      val pathDataList = clipPathsByRule[clipRule] ?: continue

      writer.write(indent)
      writer.write("<clip-path")
      if (clipRule == ClipRule.EVEN_ODD) writer.write(" android:fillType=\"evenOdd\"")

      writer.write(" android:pathData=\"")

      for (i in pathDataList.indices) {
        val path = pathDataList[i]
        if (i > 0 && !path.startsWith("M")) writer.write("M 0,0")

        writer.write(path)
      }

      writer.write("\"/>")
      writer.write(System.lineSeparator())
    }

    affectedNodes.forEach { it.writeXml(writer, indent) }
  }

  private fun OutputStreamWriter.writeGroupStart(indent: String) {
    write(indent)
    write("<group>")
    write(System.lineSeparator())
  }

  private fun OutputStreamWriter.writeGroupEnd(indent: String) {
    write(indent)
    write("</group>")
    write(System.lineSeparator())
  }
}
