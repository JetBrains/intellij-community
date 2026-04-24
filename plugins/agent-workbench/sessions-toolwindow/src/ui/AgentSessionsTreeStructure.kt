// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeModel
import com.intellij.agent.workbench.sessions.toolwindow.tree.sessionTreeNodePresentation
import com.intellij.agent.workbench.sessions.toolwindow.tree.shouldExpandOnDoubleClick
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ui.tree.LeafState

private object SessionTreeRootElement

internal fun extractSessionTreeId(value: Any?): SessionTreeId? {
  val descriptor = com.intellij.util.ui.tree.TreeUtil.getUserObject(NodeDescriptor::class.java, value) ?: return null
  return descriptor.element as? SessionTreeId
}

internal class AgentSessionsTreeStructure(
  private val modelProvider: () -> SessionTreeModel,
) : AbstractTreeStructure() {
  override fun getRootElement(): Any = SessionTreeRootElement

  override fun getChildElements(element: Any): Array<Any> {
    val model = modelProvider()
    return when (element) {
      SessionTreeRootElement -> model.rootIds.toTypedArray()
      is SessionTreeId -> model.entriesById[element]?.childIds?.toTypedArray() ?: emptyArray()
      else -> emptyArray()
    }
  }

  override fun getParentElement(element: Any): Any? {
    if (element === SessionTreeRootElement) return null
    val id = element as? SessionTreeId ?: return null
    val entry = modelProvider().entriesById[id] ?: return null
    return entry.parentId ?: SessionTreeRootElement
  }

  override fun createDescriptor(element: Any, parentDescriptor: NodeDescriptor<*>?): NodeDescriptor<*> {
    return AgentSessionsTreeNodeDescriptor(parentDescriptor, element, modelProvider)
  }

  override fun commit() = Unit

  override fun hasSomethingToCommit(): Boolean = false

  override fun getLeafState(element: Any): LeafState {
    if (element === SessionTreeRootElement) return LeafState.NEVER
    val id = element as? SessionTreeId ?: return LeafState.DEFAULT
    if (id is SessionTreeId.Project) return LeafState.NEVER
    val entry = modelProvider().entriesById[id] ?: return LeafState.DEFAULT
    return if (entry.childIds.isEmpty()) LeafState.ALWAYS else LeafState.DEFAULT
  }

  override fun isValid(element: Any): Boolean {
    if (element === SessionTreeRootElement) return true
    return element is SessionTreeId && modelProvider().entriesById.containsKey(element)
  }
}

private class AgentSessionsTreeNodeDescriptor(
  parentDescriptor: NodeDescriptor<*>?,
  private val element: Any,
  private val modelProvider: () -> SessionTreeModel,
) : NodeDescriptor<Any?>(null, parentDescriptor) {
  private var presentationHash: Int = computePresentationHash()

  override fun update(): Boolean {
    val nextHash = computePresentationHash()
    if (presentationHash == nextHash) {
      return false
    }
    presentationHash = nextHash
    return true
  }

  override fun getElement(): Any = element

  override fun expandOnDoubleClick(): Boolean {
    if (element !is SessionTreeId) return true
    val node = modelProvider().entriesById[element]?.node ?: return true
    return shouldExpandOnDoubleClick(node)
  }

  private fun computePresentationHash(): Int {
    return when (element) {
      SessionTreeRootElement -> 0
      is SessionTreeId -> {
        val node = modelProvider().entriesById[element]?.node ?: return -1
        sessionTreeNodePresentation(node).hashCode()
      }
      else -> 0
    }
  }
}
