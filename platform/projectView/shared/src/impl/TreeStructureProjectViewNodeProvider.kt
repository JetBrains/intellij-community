// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DestructuringDeclaration") // let's not use destructuring when it hurts readability

package com.intellij.platform.projectView.impl

import com.intellij.ide.projectView.NodeSortKey
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure
import com.intellij.ide.projectView.impl.GroupByTypeComparator
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.ide.util.treeView.ValidateableNode
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.pane.BackendProjectViewNodeModel
import com.intellij.platform.projectView.pane.buildProjectViewNodeModel
import com.intellij.platform.projectView.settings.ProjectViewPaneOptionImpl
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsAccessor
import com.intellij.platform.projectView.settings.toLegacySortKey
import com.intellij.ui.tree.LeafState
import com.intellij.ui.tree.buildTreeNodeDescriptorPresentation
import com.intellij.ui.treeStructure.TreeNodePresentationBuilder
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface TreeStructureProjectViewNode {
  val elementDescriptor: NodeDescriptor<*>
}

private data class TreeStructureProjectViewNodeImpl(
  val element: Any,
  override val elementDescriptor: NodeDescriptor<*>,
) : TreeStructureProjectViewNode

@ApiStatus.Experimental
class TreeStructureProjectViewNodeProvider(
  project: Project,
  structure: AbstractProjectTreeStructure,
  settings: ProjectViewPaneSettingsAccessor,
) : ProjectViewTreeNodeProvider<TreeStructureProjectViewNode> {
  private val structure = TypesafeTreeStructure(structure)
  private val semaphore = Semaphore(permits = 1)
  private val comparator = MyGroupByTypeComparator(project, settings)

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
        val root = structure.getRoot()
        root.elementDescriptor.update()
        listOf(root)
      }
      else {
        if (!structure.isValid(parent)) return@read null
        val children = structure.getChildren(parent)
        for (child in children) {
          child.elementDescriptor.update() // sadly, needed for the comparator
        }
        children.sortedWith { node1, node2 -> comparator.compare(node1.elementDescriptor, node2.elementDescriptor) }
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
    val descriptor = validNode.elementDescriptor
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

  private class MyGroupByTypeComparator(project: Project, private val settings: ProjectViewPaneSettingsAccessor) : GroupByTypeComparator(project, null) {

    override fun getSortKey(): NodeSortKey {
      return settings.getSortKey().toLegacySortKey()
    }

    override fun isManualOrder(): Boolean {
      return settings.isOptionSelected(ProjectViewPaneOptionImpl.ManualOrder)
    }

    override fun isAbbreviateQualifiedNames(): Boolean {
      return settings.isOptionSelected(ProjectViewPaneOptionImpl.AbbreviatePackageNames)
    }

    override fun isFoldersAlwaysOnTop(): Boolean {
      return settings.isOptionSelected(ProjectViewPaneOptionImpl.FoldersAlwaysOnTop)
    }
  }
}

private class TypesafeTreeStructure(
  private val structure: AbstractTreeStructure,
) {
  fun getRoot(): TreeStructureProjectViewNodeImpl {
    ThreadingAssertions.assertReadAccess()
    val rootElement = structure.rootElement
    return TreeStructureProjectViewNodeImpl(
      element = rootElement,
      elementDescriptor = structure.createDescriptor(rootElement, null),
    )
  }

  fun getChildren(parent: TreeStructureProjectViewNodeImpl): List<TreeStructureProjectViewNodeImpl> {
    ThreadingAssertions.assertReadAccess()
    val validParent = parent.takeIf { isValid(it) } ?: return emptyList()
    val parentDescriptor = validParent.elementDescriptor
    return structure.getChildElements(validParent.element).map {
      TreeStructureProjectViewNodeImpl(
        element = it,
        elementDescriptor = structure.createDescriptor(it, parentDescriptor),
      )
    }
  }

  fun isValid(node: TreeStructureProjectViewNodeImpl): Boolean {
    ThreadingAssertions.assertReadAccess()
    val element = node.element
    if (element is AbstractTreeNode<*> && element.value == null) return false
    if (element is ValidateableNode && !element.isValid) return false
    if (!structure.isValid(element)) return false
    return true
  }

  fun getLeafState(node: TreeStructureProjectViewNodeImpl): LeafState {
    ThreadingAssertions.assertReadAccess()
    return structure.getLeafState(node.element)
  }
}
