// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.ide.util.treeView.ValidateableNode
import com.intellij.openapi.application.readAction
import com.intellij.platform.projectView.pane.BackendProjectViewNodeModel
import com.intellij.platform.projectView.pane.buildProjectViewNodeModel
import com.intellij.ui.tree.LeafState
import com.intellij.ui.tree.buildTreeNodeDescriptorPresentation
import com.intellij.ui.treeStructure.TreeNodePresentationBuilder
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface TreeStructureProjectViewNode

private data class TreeStructureProjectViewNodeImpl(
  val element: Any,
  val parentDescriptor: NodeDescriptor<*>?,
) : TreeStructureProjectViewNode

@ApiStatus.Experimental
class TreeStructureProjectViewNodeProvider(
  structure: AbstractTreeStructure,
) : ProjectViewTreeNodeProvider<TreeStructureProjectViewNode> {
  private val structure = TypesafeTreeStructure(structure)
  private val semaphore = Semaphore(permits = 1)

  private suspend fun <T> read(read: () -> T): T {
    return semaphore.withPermit {
      readAction {
        read()
      }
    }
  }

  override suspend fun getChildren(parent: TreeStructureProjectViewNode?): List<TreeStructureProjectViewNode>? {
    parent as TreeStructureProjectViewNodeImpl?
    return read {
      if (parent == null) {
        listOf(structure.getRoot())
      }
      else {
        if (!structure.isValid(parent)) return@read null
        structure.getChildren(parent)
      }
    }
  }

  override suspend fun createNodeModel(id: Long, node: TreeStructureProjectViewNode): BackendProjectViewNodeModel<TreeStructureProjectViewNode>? {
    node as TreeStructureProjectViewNodeImpl?
    return read {
      if (!structure.isValid(node)) return@read null
      buildProjectViewNodeModel(id, node) { nodeBuilder ->
        nodeBuilder.buildPresentation { presentationBuilder ->
          buildPresentation(presentationBuilder, node)
        }
      }
    }
  }

  private fun buildPresentation(builder: TreeNodePresentationBuilder, validNode: TreeStructureProjectViewNodeImpl) {
    val descriptor = structure.createDescriptor(validNode)
    descriptor.update()
    builder.setLeaf(computeIsLeaf(validNode))
    if (descriptor !is PresentableNodeDescriptor<*>) {
      builder.setMainText(descriptor.toString())
      return
    }
    buildTreeNodeDescriptorPresentation(descriptor, builder)
  }

  private fun computeIsLeaf(validNode: TreeStructureProjectViewNodeImpl): Boolean {
    return when (structure.getLeafState(validNode)) {
      LeafState.ALWAYS -> true
      LeafState.NEVER -> false
      else -> structure.getChildren(validNode).isEmpty()
    }
  }
}

private class TypesafeTreeStructure(
  private val structure: AbstractTreeStructure,
) {
  fun getRoot(): TreeStructureProjectViewNodeImpl {
    ThreadingAssertions.assertReadAccess()
    return TreeStructureProjectViewNodeImpl(structure.rootElement, parentDescriptor = null)
  }

  fun getChildren(parent: TreeStructureProjectViewNodeImpl): List<TreeStructureProjectViewNodeImpl> {
    ThreadingAssertions.assertReadAccess()
    val validParent = parent.takeIf { isValid(it) } ?: return emptyList()
    val parentDescriptor = createDescriptor(validParent)
    return structure.getChildElements(validParent.element).map { TreeStructureProjectViewNodeImpl(it, parentDescriptor) }
  }

  fun isValid(node: TreeStructureProjectViewNodeImpl): Boolean {
    ThreadingAssertions.assertReadAccess()
    val element = node.element
    if (element is AbstractTreeNode<*> && element.value == null) return false
    if (element is ValidateableNode && !element.isValid) return false
    if (!structure.isValid(element)) return false
    return true
  }

  fun createDescriptor(node: TreeStructureProjectViewNodeImpl): NodeDescriptor<*> {
    ThreadingAssertions.assertReadAccess()
    return structure.createDescriptor(node.element, node.parentDescriptor)
  }

  fun getLeafState(node: TreeStructureProjectViewNodeImpl): LeafState {
    ThreadingAssertions.assertReadAccess()
    return structure.getLeafState(node.element)
  }
}
