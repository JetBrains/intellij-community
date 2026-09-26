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

import com.intellij.compose.ide.plugin.resources.vectorDrawable.svgConverter.ComposeResourcesXmlParser.getStartLine
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.nio.file.Path
import kotlin.math.max

/*
 * Based on [com.android.ide.common.vectordrawable.Svg2Vector]
 * Java source: https://cs.android.com/android-studio/platform/tools/base/+/74a5c9785c3ad9f7f0ce8fbf4adfb2ae2c6bec2c:sdk-common/src/main/java/com/android/ide/common/vectordrawable/Svg2Vector.java
 */
internal class ComposeResourcesSvgConverter {
  fun parse(file: Path): ComposeResourcesSvgTree {
    val svgTree = ComposeResourcesSvgTree()
    val parseErrors = mutableListOf<String?>()

    val doc = svgTree.parse(file, parseErrors)
    parseErrors.forEach { svgTree.logError(it, null) }
    if (doc == null) return svgTree

    val svgNodes = doc.getElementsByTagName("svg")
    if (svgNodes.length != 1) {
      val message = if (svgNodes.length == 0) "No <svg> tags found." else "Multiple <svg> tags are not supported."
      throw IllegalStateException(message)
    }

    val rootElement = svgNodes.item(0) as Element
    svgTree.parseDimension(rootElement)

    if (svgTree.viewBox == null) {
      svgTree.logError("Missing \"viewBox\" in <svg> element", rootElement)
      return svgTree
    }

    val root = ComposeResourcesSvgGroupNode(svgTree, rootElement, "root")
    svgTree.root = root

    traverseSvgAndExtract(svgTree, root, rootElement)
    resolveUseNodes(svgTree)
    resolveGradientReferences(svgTree)

    for ((key, nodes) in svgTree.getStyleAffectedNodes()) {
      nodes.forEach { addStyleToPath(it, svgTree.getStyleClassAttr(key)) }
    }

    for ((node, pair) in svgTree.getClipPathAffectedNodesSet()) {
      handleClipPath(svgTree, node, pair.first, pair.second)
    }

    svgTree.flatten()
    svgTree.validate()
    svgTree.dump()

    return svgTree
  }

  private fun resolveUseNodes(svgTree: ComposeResourcesSvgTree) {
    val nodes = svgTree.getPendingUseSet()
    val pendingUseSet = HashSet(nodes)

    while (nodes.isNotEmpty()) {
      if (nodes.removeIf { it.resolveHref(svgTree) }) continue
      reportCycles(svgTree, nodes)
      return
    }

    val ordering = getUseNodeTopologicalOrdering(svgTree, pendingUseSet)
    ordering.forEach { it.handleUse() }
  }

  private fun getUseNodeTopologicalOrdering(
    svgTree: ComposeResourcesSvgTree,
    pendingUseSet: MutableSet<ComposeResourcesSvgGroupNode>,
  ): MutableList<ComposeResourcesSvgGroupNode> {
    val queue = ArrayDeque<ComposeResourcesSvgNode>()
    val reverseGraph = HashMap<ComposeResourcesSvgNode, MutableSet<ComposeResourcesSvgNode>>()
    val inDegrees = HashMap<ComposeResourcesSvgNode, Int>()

    val root = svgTree.root ?: return mutableListOf()

    queue.add(root)
    reverseGraph[root] = HashSet()
    inDegrees[root] = 0

    while (queue.isNotEmpty()) {
      val current = queue.removeFirst() as? ComposeResourcesSvgGroupNode ?: continue

      for (child in current.children) {
        reverseGraph.getOrPut(child) { HashSet() }.add(current)
        if (child in inDegrees) continue
        queue.addLast(child)
        inDegrees[child] = 0
      }

      val useRefNode = current.useReferenceNode ?: continue
      reverseGraph.getOrPut(useRefNode) { HashSet() }.add(current)
      if (useRefNode in inDegrees) continue
      queue.addLast(useRefNode)
      inDegrees[useRefNode] = 0
    }

    for ((_, children) in reverseGraph) {
      for (child in children) {
        inDegrees[child] = inDegrees.getValue(child) + 1
      }
    }

    for ((node, degree) in inDegrees) {
      if (degree == 0) queue.add(node)
    }

    val topologicalOrdering = ArrayList<ComposeResourcesSvgGroupNode>()
    while (queue.isNotEmpty()) {
      val current = queue.removeFirst()
      if (current is ComposeResourcesSvgGroupNode && current.useReferenceNode != null) {
        topologicalOrdering.add(current)
        pendingUseSet.remove(current)
      }

      for (child in reverseGraph.getValue(current)) {
        inDegrees[child] = inDegrees.getValue(child) - 1
        if (inDegrees.getValue(child) == 0) queue.add(child)
      }
    }

    topologicalOrdering.addAll(pendingUseSet)
    return topologicalOrdering
  }

  private fun resolveGradientReferences(svgTree: ComposeResourcesSvgTree) {
    val nodes = svgTree.getPendingGradientRefSet()
    while (nodes.isNotEmpty()) {
      if (nodes.removeIf { it.resolveHref(svgTree) }) continue
      reportCycles(svgTree, nodes)
      break
    }
  }

  private fun <T : ComposeResourcesSvgNode> reportCycles(
    svgTree: ComposeResourcesSvgTree, svgNodes: MutableSet<T>,
  ) {
    val edges = HashMap<String, String>()
    val nodesById = HashMap<String, Node>()

    for (svgNode in svgNodes) {
      val element = svgNode.documentElement
      val id = element.getAttribute("id")
      val targetId = svgNode.getHrefId()

      if (id.isEmpty() || targetId.isEmpty()) continue
      edges[id] = targetId
      nodesById[id] = element
    }

    while (edges.isNotEmpty()) {
      val visited = HashSet<String>()
      val entry = edges.entries.first()
      var id = entry.key
      var targetId: String? = entry.value

      while (targetId != null && visited.add(id)) {
        id = targetId
        targetId = edges[id]
      }

      if (targetId != null) {
        val node = nodesById[id]
        val cycle = getCycleStartingAt(id, edges, nodesById)
        svgTree.logError("Circular dependency of <use> nodes: $cycle", node)
      }
      edges.keys.removeAll(visited)
    }
  }

  private fun getCycleStartingAt(
    startId: String?,
    edges: Map<String, String>,
    nodesById: Map<String, Node>,
  ): String = buildString {
    append(startId)
    var id = startId
    while (true) {
      id = edges[id] ?: break
      append(" -> ").append(id)
      if (id == startId) break
      val line = nodesById[id]?.let { getStartLine(it) } ?: 0
      append(" (line ").append(line).append(")")
    }
  }

  private fun traverseSvgAndExtract(
    svgTree: ComposeResourcesSvgTree, currentGroup: ComposeResourcesSvgGroupNode, item: Element,
  ) {
    val childNodes = item.childNodes

    for (i in 0 until childNodes.length) {
      val childNode = childNodes.item(i)
      if (childNode.nodeType != Node.ELEMENT_NODE) continue
      if (!childNode.hasChildNodes() && !childNode.hasAttributes()) continue

      val childElement = childNode as Element
      val tagName = childElement.tagName

      when (tagName) {
        "path", "rect", "circle", "ellipse", "polygon", "polyline", "line" -> {
          val child = ComposeResourcesSvgLeafNode(svgTree, childElement, tagName + i)
          processIdName(svgTree, child)
          currentGroup.addChild(child)
          extractAllItemsAs(svgTree, child, childElement, currentGroup)
          svgTree.hasLeafNode = true
        }
        "g" -> {
          val childGroup = ComposeResourcesSvgGroupNode(svgTree, childElement, "child$i")
          currentGroup.addChild(childGroup)
          processIdName(svgTree, childGroup)
          extractGroupNode(svgTree, childGroup, currentGroup)
          traverseSvgAndExtract(svgTree, childGroup, childElement)
        }
        "use" -> {
          val childGroup = ComposeResourcesSvgGroupNode(svgTree, childElement, "child$i")
          processIdName(svgTree, childGroup)
          currentGroup.addChild(childGroup)
          svgTree.addToPendingUseSet(childGroup)
        }
        "defs" -> {
          val childGroup = ComposeResourcesSvgGroupNode(svgTree, childElement, "child$i")
          traverseSvgAndExtract(svgTree, childGroup, childElement)
        }
        "clipPath", "mask" -> {
          val clipPath = ComposeResourcesSvgClipPathNode(svgTree, childElement, tagName + i)
          processIdName(svgTree, clipPath)
          traverseSvgAndExtract(svgTree, clipPath, childElement)
        }
        "style" -> extractStyleNode(svgTree, childElement)
        "linearGradient", "radialGradient" -> {
          val gradientNode = ComposeResourcesSvgGradientNode(svgTree, childElement, tagName + i)
          processIdName(svgTree, gradientNode)
          extractGradientNode(svgTree, gradientNode)
          val type = if (tagName == "linearGradient") "linear" else "radial"
          gradientNode.fillPresentationAttributes("gradientType", type)
          svgTree.hasGradient = true
        }
        else -> {
          val id = childElement.getAttribute("id")
          if (id.isNotEmpty()) svgTree.addIgnoredId(id)
          if (unsupportedSvgNodes.contains(tagName)) svgTree.logError("<$tagName> is not supported", childElement)
          traverseSvgAndExtract(svgTree, currentGroup, childElement)
        }
      }
    }
  }

  private fun extractGradientNode(svg: ComposeResourcesSvgTree, gradientNode: ComposeResourcesSvgGradientNode) {
    val element = gradientNode.documentElement
    val attrs = element.attributes
    if (attrs.getNamedItem("href") != null || attrs.getNamedItem("xlink:href") != null) {
      svg.addToPendingGradientRefSet(gradientNode)
    }

    for (j in 0 until attrs.length) {
      val n = attrs.item(j)
      val name = n.nodeName ?: continue
      val value = n.nodeValue ?: continue
      if (gradientMap.containsKey(name)) gradientNode.fillPresentationAttributes(name, value)
    }

    val gradientChildren = element.childNodes
    var greatestOffset = 0.0

    for (i in 0 until gradientChildren.length) {
      val node = gradientChildren.item(i)
      if (node.nodeName != "stop") continue

      val stopAttr = node.attributes
      var color = "rgb(0,0,0)"
      var opacity = "1"

      for (k in 0 until stopAttr.length) {
        val stopItem = stopAttr.item(k)
        val name = stopItem.nodeName ?: continue
        val value = stopItem.nodeValue ?: continue

        try {
          when (name) {
            "offset" -> greatestOffset = extractOffset(value, greatestOffset)
            "stop-color" -> color = value
            "stop-opacity" -> opacity = value
            "style" -> {
              for (attr in value.split(";")) {
                val splitAttribute = attr.split(":")
                if (splitAttribute.size != 2) continue
                if (attr.startsWith("stop-color")) color = splitAttribute[1]
                else if (attr.startsWith("stop-opacity")) opacity = splitAttribute[1]
              }
            }
          }
        }
        catch (_: IllegalArgumentException) {
          svg.logError("Invalid attribute value: $name=\"$value\"", node)
        }
      }
      val offset = svg.formatCoordinate(greatestOffset)
      color = gradientNode.colorSvg2Vd(color, "#000000") ?: color
      gradientNode.addGradientStop(color, offset, opacity)
    }
  }

  private fun extractOffset(offset: String, greatestOffset: Double): Double {
    var x = if (offset.endsWith("%")) offset.removeSuffix("%").toDouble() / 100.0 else offset.toDouble()

    x = x.coerceIn(0.0, 1.0)
    return max(x, greatestOffset)
  }

  private fun extractGroupNode(
    svgTree: ComposeResourcesSvgTree,
    childGroup: ComposeResourcesSvgGroupNode,
    currentGroup: ComposeResourcesSvgGroupNode,
  ) {
    val a = childGroup.documentElement.attributes
    for (j in 0 until a.length) {
      val n = a.item(j)
      val name = n.nodeName ?: continue
      val value = n.nodeValue ?: continue
      if (value.isEmpty()) continue

      when (name) {
        "clip-path", "mask" -> svgTree.addClipPathAffectedNode(childGroup, currentGroup, value)
        "class" -> svgTree.addAffectedNodeToStyleClass(".$value", childGroup)
      }
    }
  }

  private fun extractStyleNode(svgTree: ComposeResourcesSvgTree, currentNode: Node) {
    val a = currentNode.childNodes
    var styleData = ""
    for (j in 0 until a.length) {
      val n = a.item(j)
      if (n.nodeType == Node.CDATA_SECTION_NODE || a.length == 1) {
        styleData = n.nodeValue ?: ""
      }
    }

    if (styleData.isEmpty()) return

    for (aClassData in styleData.split("}")) {
      val splitClassData = aClassData.split("{")
      if (splitClassData.size < 2) continue

      val classNameRaw = splitClassData[0].trim()
      val styleAttr = splitClassData[1].trim()

      for (splitClassName in classNameRaw.split(",")) {
        val className = splitClassName.trim()
        val existing = svgTree.getStyleClassAttr(className)
        val styleAttrTemp = if (existing != null) "$styleAttr;$existing" else styleAttr
        svgTree.addStyleClassToTree(className, styleAttrTemp)
      }
    }
  }

  private fun processIdName(svgTree: ComposeResourcesSvgTree, node: ComposeResourcesSvgNode) {
    val id = node.getAttributeValue("id")
    if (id.isNotEmpty()) svgTree.addIdToMap(id, node)
  }

  private fun handleClipPath(
    svg: ComposeResourcesSvgTree,
    child: ComposeResourcesSvgNode,
    currentGroup: ComposeResourcesSvgGroupNode?,
    value: String?,
  ) {
    if (currentGroup == null || value == null) return
    val clipName = getClipPathName(value) ?: return
    val clipNode = svg.getSvgNodeFromId(clipName) ?: return

    val clipCopy = (clipNode as ComposeResourcesSvgClipPathNode).deepCopy()
    currentGroup.replaceChild(child, clipCopy)
    clipCopy.addAffectedNode(child)
    clipCopy.setClipPathNodeAttributes()
  }

  private fun getClipPathName(s: String?): String? {
    if (s == null) return null
    val startPos = s.indexOf('#')
    var endPos = s.indexOf(')', startPos + 1)
    if (endPos < 0) endPos = s.length
    return s.substring(startPos + 1, endPos).trim()
  }

  private fun extractAllItemsAs(
    svg: ComposeResourcesSvgTree,
    child: ComposeResourcesSvgLeafNode,
    currentItem: Node,
    currentGroup: ComposeResourcesSvgGroupNode,
  ) {
    var parentNode = currentItem.parentNode
    var hasNodeAttr = false
    var styleContent = ""
    val styleContentBuilder = StringBuilder()
    var nothingToDisplay = false

    while (parentNode != null && parentNode.nodeName == "g") {
      val attr = parentNode.attributes
      attr.getNamedItem("style")?.let {
        styleContentBuilder.append(it.textContent).append(';')
        styleContent = styleContentBuilder.toString()
        if (styleContent.contains("display:none")) nothingToDisplay = true
        else hasNodeAttr = true
      }

      if (attr.getNamedItem("display")?.nodeValue == "none") nothingToDisplay = true

      if (nothingToDisplay) break
      parentNode = parentNode.parentNode
    }

    if (nothingToDisplay) return
    if (hasNodeAttr && styleContent.isNotEmpty()) addStyleToPath(child, styleContent)

    when (currentItem.nodeName) {
      "path" -> extractPathItem(svg, child, currentItem, currentGroup)
      "rect" -> extractRectItem(svg, child, currentItem, currentGroup)
      "circle" -> extractCircleItem(svg, child, currentItem, currentGroup)
      "polygon", "polyline" -> extractPolyItem(svg, child, currentItem, currentGroup)
      "line" -> extractLineItem(svg, child, currentItem, currentGroup)
      "ellipse" -> extractEllipseItem(svg, child, currentItem, currentGroup)
    }

    currentItem.nodeName.let { svg.addAffectedNodeToStyleClass(it, child) }
  }

  private fun extractPolyItem(
    svgTree: ComposeResourcesSvgTree, child: ComposeResourcesSvgLeafNode,
    currentGroupNode: Node, currentGroup: ComposeResourcesSvgGroupNode,
  ) {
    if (currentGroupNode.nodeType != Node.ELEMENT_NODE) return

    val attributes = currentGroupNode.attributes
    for (itemIndex in 0 until attributes.length) {
      val n = attributes.item(itemIndex)
      val name = n.nodeName ?: continue
      val value = n.nodeValue ?: continue

      try {
        when (name) {
          "style" -> addStyleToPath(child, value)
          in presentationMap -> child.fillPresentationAttributes(name, value)
          "clip-path", "mask" -> svgTree.addClipPathAffectedNode(child, currentGroup, value)
          "points" -> {
            val builder = ComposeResourcesPathBuilder()
            val split = value.split("[\\s,]+".toRegex()).filter { it.isNotEmpty() }
            var baseX = split[0].toFloat()
            var baseY = split[1].toFloat()
            builder.absoluteMoveTo(baseX.toDouble(), baseY.toDouble())

            for (j in 2 until split.size step 2) {
              val x = split[j].toFloat()
              val y = split[j + 1].toFloat()
              builder.relativeLineTo((x - baseX).toDouble(), (y - baseY).toDouble())
              baseX = x
              baseY = y
            }
            if ("polygon" == currentGroupNode.nodeName) builder.relativeClose()
            child.pathData = builder.toString()
          }
          "class" -> applyStyleClasses(svgTree, currentGroupNode.nodeName, value, child)
        }
      }
      catch (e: Exception) {
        if (e is NumberFormatException || e is ArrayIndexOutOfBoundsException) {
          svgTree.logError("Invalid value of \"$name\" attribute", n)
        }
        else throw e
      }
    }
  }

  private fun extractRectItem(
    svg: ComposeResourcesSvgTree, child: ComposeResourcesSvgLeafNode,
    currentGroupNode: Node, currentGroup: ComposeResourcesSvgGroupNode,
  ) {
    if (currentGroupNode.nodeType != Node.ELEMENT_NODE) return

    var x = 0.0
    var y = 0.0
    var rx = 0.0
    var ry = 0.0
    var width = Double.NaN
    var height = Double.NaN
    var pureTransparent = false
    val attributes = currentGroupNode.attributes

    for (j in 0 until attributes.length) {
      val name = attributes.item(j).nodeName ?: continue
      val value = attributes.item(j).nodeValue ?: continue

      try {
        when {
          name == "style" -> {
            addStyleToPath(child, value)
            if (value.contains("opacity:0;")) pureTransparent = true
          }
          presentationMap.containsKey(name) -> child.fillPresentationAttributes(name, value)
          name == "clip-path" || name == "mask" -> svg.addClipPathAffectedNode(child, currentGroup, value)
          name == "x" -> x = svg.parseXValue(value)
          name == "y" -> y = svg.parseYValue(value)
          name == "rx" -> rx = svg.parseXValue(value)
          name == "ry" -> ry = svg.parseYValue(value)
          name == "width" -> width = svg.parseXValue(value)
          name == "height" -> height = svg.parseYValue(value)
          name == "class" -> applyStyleClasses(svg, currentGroupNode.nodeName, value, child)
        }
      }
      catch (_: IllegalArgumentException) {
        svg.logError("Invalid attribute value: $name=\"$value\"", currentGroupNode)
      }
    }

    if (!pureTransparent && !x.isNaN() && !y.isNaN() && !width.isNaN() && !height.isNaN()) {
      val builder = ComposeResourcesPathBuilder()
      if (rx <= 0 && ry <= 0) {
        builder.absoluteMoveTo(x, y)
        builder.relativeHorizontalTo(width)
        builder.relativeVerticalTo(height)
        builder.relativeHorizontalTo(-width)
      }
      else {
        if (ry == 0.0) ry = rx else if (rx == 0.0) rx = ry
        if (rx > width / 2) rx = width / 2
        if (ry > height / 2) ry = height / 2

        builder.absoluteMoveTo(x + rx, y)
        builder.absoluteLineTo(x + width - rx, y)
        builder.absoluteArcTo(rx, ry, false, false, true, x + width, y + ry)
        builder.absoluteLineTo(x + width, y + height - ry)
        builder.absoluteArcTo(rx, ry, false, false, true, x + width - rx, y + height)
        builder.absoluteLineTo(x + rx, y + height)
        builder.absoluteArcTo(rx, ry, false, false, true, x, y + height - ry)
        builder.absoluteLineTo(x, y + ry)
        builder.absoluteArcTo(rx, ry, false, false, true, x + rx, y)
      }
      builder.relativeClose()
      child.pathData = builder.toString()
    }
  }

  private fun extractCircleItem(
    svg: ComposeResourcesSvgTree, child: ComposeResourcesSvgLeafNode,
    currentGroupNode: Node, currentGroup: ComposeResourcesSvgGroupNode,
  ) {
    if (currentGroupNode.nodeType != Node.ELEMENT_NODE) return

    var cx = 0f
    var cy = 0f
    var radius = 0f
    var pureTransparent = false
    val attributes = currentGroupNode.attributes

    for (j in 0 until attributes.length) {
      val name = attributes.item(j).nodeName ?: continue
      val value = attributes.item(j).nodeValue ?: continue

      when {
        name == "style" -> {
          addStyleToPath(child, value)
          if (value.contains("opacity:0;")) pureTransparent = true
        }
        presentationMap.containsKey(name) -> child.fillPresentationAttributes(name, value)
        name == "clip-path" || name == "mask" -> svg.addClipPathAffectedNode(child, currentGroup, value)
        name == "cx" -> cx = value.toFloat()
        name == "cy" -> cy = value.toFloat()
        name == "r" -> radius = value.toFloat()
        name == "class" -> applyStyleClasses(svg, currentGroupNode.nodeName, value, child)
      }
    }

    if (!pureTransparent && !cx.isNaN() && !cy.isNaN()) {
      val builder = ComposeResourcesPathBuilder()
      builder.absoluteMoveTo(cx.toDouble(), cy.toDouble())
      builder.relativeMoveTo(-radius.toDouble(), 0.0)
      builder.relativeArcTo(radius.toDouble(), radius.toDouble(), false, true, true, (2 * radius).toDouble(), 0.0)
      builder.relativeArcTo(radius.toDouble(), radius.toDouble(), false, true, true, (-2 * radius).toDouble(), 0.0)
      child.pathData = builder.toString()
    }
  }

  private fun extractEllipseItem(
    svg: ComposeResourcesSvgTree, child: ComposeResourcesSvgLeafNode,
    currentGroupNode: Node, currentGroup: ComposeResourcesSvgGroupNode,
  ) {
    if (currentGroupNode.nodeType != Node.ELEMENT_NODE) return

    var cx = 0f
    var cy = 0f
    var rx = 0f
    var ry = 0f
    var pureTransparent = false
    val attributes = currentGroupNode.attributes

    for (j in 0 until attributes.length) {
      val name = attributes.item(j).nodeName ?: continue
      val value = attributes.item(j).nodeValue ?: continue

      when {
        name == "style" -> {
          addStyleToPath(child, value)
          if (value.contains("opacity:0;")) pureTransparent = true
        }
        presentationMap.containsKey(name) -> child.fillPresentationAttributes(name, value)
        name == "clip-path" || name == "mask" -> svg.addClipPathAffectedNode(child, currentGroup, value)
        name == "cx" -> cx = value.toFloat()
        name == "cy" -> cy = value.toFloat()
        name == "rx" -> rx = value.toFloat()
        name == "ry" -> ry = value.toFloat()
        name == "class" -> applyStyleClasses(svg, currentGroupNode.nodeName, value, child)
      }
    }

    if (!pureTransparent && !cx.isNaN() && !cy.isNaN() && rx > 0 && ry > 0) {
      val builder = ComposeResourcesPathBuilder()
      builder.absoluteMoveTo((cx - rx).toDouble(), cy.toDouble())
      builder.relativeArcTo(rx.toDouble(), ry.toDouble(), false, true, false, (2 * rx).toDouble(), 0.0)
      builder.relativeArcTo(rx.toDouble(), ry.toDouble(), false, true, false, (-2 * rx).toDouble(), 0.0)
      builder.relativeClose()
      child.pathData = builder.toString()
    }
  }

  private fun extractLineItem(
    svg: ComposeResourcesSvgTree, child: ComposeResourcesSvgLeafNode,
    currentGroupNode: Node, currentGroup: ComposeResourcesSvgGroupNode,
  ) {
    if (currentGroupNode.nodeType != Node.ELEMENT_NODE) return

    var x1 = 0f
    var y1 = 0f
    var x2 = 0f
    var y2 = 0f
    var pureTransparent = false
    val attributes = currentGroupNode.attributes

    for (j in 0 until attributes.length) {
      val name = attributes.item(j).nodeName ?: continue
      val value = attributes.item(j).nodeValue ?: continue

      when (name) {
        "style" -> {
          addStyleToPath(child, value)
          if (value.contains("opacity:0;")) pureTransparent = true
        }
        in presentationMap.keys -> child.fillPresentationAttributes(name, value)
        "clip-path", "mask" -> svg.addClipPathAffectedNode(child, currentGroup, value)
        "x1" -> x1 = value.toFloat()
        "y1" -> y1 = value.toFloat()
        "x2" -> x2 = value.toFloat()
        "y2" -> y2 = value.toFloat()
        "class" -> applyStyleClasses(svg, currentGroupNode.nodeName, value, child)
      }
    }

    if (!pureTransparent && !x1.isNaN() && !y1.isNaN() && !x2.isNaN() && !y2.isNaN()) {
      val builder = ComposeResourcesPathBuilder()
      builder.absoluteMoveTo(x1.toDouble(), y1.toDouble())
      builder.absoluteLineTo(x2.toDouble(), y2.toDouble())
      child.pathData = builder.toString()
    }
  }

  private fun extractPathItem(
    svg: ComposeResourcesSvgTree, child: ComposeResourcesSvgLeafNode,
    currentGroupNode: Node, currentGroup: ComposeResourcesSvgGroupNode,
  ) {
    if (currentGroupNode.nodeType != Node.ELEMENT_NODE) return

    val attributes = currentGroupNode.attributes
    for (j in 0 until attributes.length) {
      val name = attributes.item(j).nodeName ?: continue
      val value = attributes.item(j).nodeValue ?: continue

      when (name) {
        "style" -> addStyleToPath(child, value)
        in presentationMap -> child.fillPresentationAttributes(name, value)
        "clip-path", "mask" -> svg.addClipPathAffectedNode(child, currentGroup, value)
        "d" -> child.pathData = value.replace("(\\d)-".toRegex(), "$1,-")
        "class" -> applyStyleClasses(svg, currentGroupNode.nodeName, value, child)
      }
    }
  }

  private fun addStyleToPath(path: ComposeResourcesSvgNode, value: String?) {
    if (value.isNullOrBlank()) return

    for (subStyle in value.split(";").reversed()) {
      val nameValue = subStyle.split(":")
      if (nameValue.size != 2 || nameValue[0].isBlank() || nameValue[1].isBlank()) continue

      val attr = nameValue[0].trim()
      val attrValue = nameValue[1].trim()

      when (attr) {
        in presentationMap -> path.fillPresentationAttributes(attr, attrValue)
        "opacity" -> path.fillPresentationAttributes("fill-opacity", attrValue)
        "clip-path", "mask" -> {
          path.svgTree.findParent(path)?.let {
            path.svgTree.addClipPathAffectedNode(path, it, attrValue)
          }
        }
      }
    }
  }

  private fun applyStyleClasses(svg: ComposeResourcesSvgTree, nodeName: String, value: String, child: ComposeResourcesSvgLeafNode) {
    svg.addAffectedNodeToStyleClass("$nodeName.$value", child)
    svg.addAffectedNodeToStyleClass(".$value", child)
  }

  companion object {
    val presentationMap = mapOf(
      "clip" to "android:clip",
      "clip-rule" to "",
      "fill" to "android:fillColor",
      "fill-opacity" to "android:fillAlpha",
      "fill-rule" to "android:fillType",
      "opacity" to "",
      "paint-order" to "",
      "stroke" to "android:strokeColor",
      "stroke-linecap" to "android:strokeLineCap",
      "stroke-linejoin" to "android:strokeLineJoin",
      "stroke-opacity" to "android:strokeAlpha",
      "stroke-width" to "android:strokeWidth"
    )

    val gradientMap = mapOf(
      "x1" to "android:startX",
      "y1" to "android:startY",
      "x2" to "android:endX",
      "y2" to "android:endY",
      "cx" to "android:centerX",
      "cy" to "android:centerY",
      "r" to "android:gradientRadius",
      "spreadMethod" to "android:tileMode",
      "gradientUnits" to "",
      "gradientTransform" to "",
      "gradientType" to "android:type"
    )

    private val unsupportedSvgNodes = setOf(
      "animate", "animateColor", "animateMotion", "animateTransform", "mpath", "set",
      "a", "marker", "missing-glyph", "pattern", "switch",
      "feBlend", "feColorMatrix", "feComponentTransfer", "feComposite", "feConvolveMatrix",
      "feDiffuseLighting", "feDisplacementMap", "feFlood", "feFuncA", "feFuncB", "feFuncG",
      "feFuncR", "feGaussianBlur", "feImage", "feMerge", "feMergeNode", "feMorphology",
      "feOffset", "feSpecularLighting", "feTile", "feTurbulence",
      "font", "font-face", "font-face-format", "font-face-name", "font-face-src",
      "font-face-uri", "hkern", "vkern", "stop", "ellipse", "image",
      "feDistantLight", "fePointLight", "feSpotLight", "symbol",
      "altGlyphDef", "altGlyphItem", "glyph", "glyphRef", "text",
      "altGlyph", "textPath", "tref", "tspan",
      "color-profile", "cursor", "filter", "foreignObject", "script", "view"
    )
  }
}