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

import com.intellij.openapi.diagnostic.logger
import org.w3c.dom.Element
import java.awt.geom.AffineTransform
import java.io.IOException
import java.io.OutputStreamWriter

/*
 * Based on [com.android.ide.common.vectordrawable.SvgGroupNode]
 * Java source: https://cs.android.com/android-studio/platform/tools/base/+/3012cec8bcc75944cbdc948a43abd35c5bd24d2d:sdk-common/src/main/java/com/android/ide/common/vectordrawable/SvgGroupNode.java
 */
internal open class ComposeResourcesSvgGroupNode(
  svgTree: ComposeResourcesSvgTree,
  documentElement: Element,
  name: String?,
) : ComposeResourcesSvgNode(svgTree, documentElement, name) {

  val children = mutableListOf<ComposeResourcesSvgNode>()
  var useReferenceNode: ComposeResourcesSvgNode? = null

  override val isGroupNode = true

  override fun deepCopy(): ComposeResourcesSvgGroupNode {
    val newInstance = ComposeResourcesSvgGroupNode(svgTree, documentElement, name)
    newInstance.copyFrom(this)
    return newInstance
  }

  override fun copyFrom(from: ComposeResourcesSvgNode) {
    super.copyFrom(from)
    if (from !is ComposeResourcesSvgGroupNode) return
    for (child in from.children) {
      addChild(child.deepCopy())
    }
  }

  fun resolveHref(tree: ComposeResourcesSvgTree): Boolean {
    val id = getHrefId()
    useReferenceNode = if (id.isEmpty()) null else tree.getSvgNodeFromId(id)

    val refNode = useReferenceNode
    if (refNode == null) {
      if (id.isNotEmpty() && !tree.isIdIgnored(id)) {
        tree.logError("Referenced id not found", documentElement)
      }
    }
    else if (refNode in tree.getPendingUseSet()) {
      return false
    }
    return true
  }

  fun handleUse() {
    val refNode = useReferenceNode ?: return

    val copiedNode = refNode.deepCopy()
    addChild(copiedNode)

    for ((key, value) in vdAttributesMap) {
      copiedNode.fillPresentationAttributes(key, value)
    }
    fillEmptyAttributes(vdAttributesMap)

    val x = documentElement.getAttribute("x").toFloatOrNull() ?: 0f
    val y = documentElement.getAttribute("y").toFloatOrNull() ?: 0f
    transformIfNeeded(AffineTransform(1.0, 0.0, 0.0, 1.0, x.toDouble(), y.toDouble()))
  }

  open fun addChild(child: ComposeResourcesSvgNode) {
    children.add(child)
    child.fillEmptyAttributes(vdAttributesMap)
  }

  fun replaceChild(oldChild: ComposeResourcesSvgNode, newChild: ComposeResourcesSvgNode) {
    val index = children.indexOf(oldChild)
    require(index >= 0) { "The child being replaced doesn't belong to this group" }
    children[index] = newChild
  }

  fun findParent(node: ComposeResourcesSvgNode): ComposeResourcesSvgGroupNode? {
    return children.firstNotNullOfOrNull { child ->
      when {
        child === node -> this
        child is ComposeResourcesSvgGroupNode -> child.findParent(node)
        else -> null
      }
    }
  }

  override fun dumpNode(indent: String) {
    LOG.debug("${indent}group: $name")
    children.forEach { it.dumpNode(indent + INDENT_UNIT) }
  }

  override fun transformIfNeeded(finalTransform: AffineTransform) {
    children.forEach { it.transformIfNeeded(finalTransform) }
  }

  override fun flatten(transform: AffineTransform) {
    for (child in children) {
      stackedTransform.setTransform(transform)
      stackedTransform.concatenate(localTransform)
      child.flatten(stackedTransform)
    }
  }

  override fun validate() = children.forEach { it.validate() }

  @Throws(IOException::class)
  override fun writeXml(writer: OutputStreamWriter, indent: String) = children.forEach { it.writeXml(writer, indent) }

  override fun accept(visitor: Visitor): VisitResult {
    return when (visitor.visit(this)) {
      VisitResult.CONTINUE -> {
        if (children.any { it.accept(visitor) == VisitResult.ABORT }) VisitResult.ABORT else VisitResult.CONTINUE
      }
      VisitResult.SKIP_CHILDREN -> VisitResult.CONTINUE
      VisitResult.ABORT -> VisitResult.ABORT
    }
  }

  override fun fillPresentationAttributes(name: String, value: String) {
    super.fillPresentationAttributes(name, value)
    children.filter { name !in it.vdAttributesMap }.forEach { it.fillPresentationAttributes(name, value) }
  }

  companion object {
    private val LOG = logger<ComposeResourcesSvgGroupNode>()
  }
}