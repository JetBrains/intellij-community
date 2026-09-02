// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.impl.scope

import com.intellij.ide.projectView.NodeSortKey
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.impl.GroupByTypeComparator
import com.intellij.ide.scopeView.ScopeViewTreeModel
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.impl.ProjectViewTreeNodeProvider
import com.intellij.platform.projectView.impl.TreeStructureProjectViewNode
import com.intellij.platform.projectView.pane.BackendProjectViewNodeModel
import com.intellij.platform.projectView.pane.buildProjectViewNodeModel
import com.intellij.platform.projectView.settings.ProjectViewPaneOption
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsAccessor
import com.intellij.platform.projectView.settings.toLegacySortKey
import com.intellij.pom.Navigatable
import com.intellij.ui.tree.buildTreeNodeDescriptorPresentation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jetbrains.concurrency.await

internal class ScopeViewNodeProvider(
  project: Project,
  private val treeModel: ScopeViewTreeModel,
  settings: ProjectViewPaneSettingsAccessor,
) : ProjectViewTreeNodeProvider<TreeStructureProjectViewNode> {
  private val comparator = ScopeViewComparator(project, settings)

  /**
   * Runs [compute] on the model's own single background thread, which is where [ScopeViewTreeModel] expects to be
   * called from (off that thread it silently returns nothing). That thread already holds a read action, so no
   * `readAction` here: it could move the computation to another thread.
   */
  private suspend fun <T> onInvoker(compute: () -> T): T = treeModel.invoker.compute(compute).await()

  override suspend fun getChildren(parent: TreeStructureProjectViewNode?): List<TreeStructureProjectViewNode>? = onInvoker {
    if (parent == null) {
      val root = treeModel.root as? AbstractTreeNode<*> ?: return@onInvoker null
      listOf(ScopeViewNode(root))
    }
    else {
      val parentNode = parent.legacyNode
      if (!isValid(parentNode)) return@onInvoker null
      // The model already re-parents and updates each child, but it doesn't sort them:
      // its own comparator is deliberately left unset so that we can sort by the new settings.
      treeModel.getChildren(parentNode)
        .sortedWith(comparator)
        .map { ScopeViewNode(it) }
    }
  }

  override suspend fun getNodeModelFlow(
    id: Long,
    node: TreeStructureProjectViewNode,
  ): Flow<BackendProjectViewNodeModel<TreeStructureProjectViewNode>> = flow {
    val model = onInvoker { buildModel(id, node) } ?: return@flow
    emit(model)
  }

  private fun buildModel(id: Long, node: TreeStructureProjectViewNode): BackendProjectViewNodeModel<TreeStructureProjectViewNode>? {
    val legacyNode = node.legacyNode
    if (!isValid(legacyNode)) return null
    return buildProjectViewNodeModel(id, node) { nodeBuilder ->
      nodeBuilder.buildPresentation { presentationBuilder ->
        legacyNode.update()
        buildTreeNodeDescriptorPresentation(legacyNode, presentationBuilder)
        presentationBuilder.setLeaf(treeModel.isLeaf(legacyNode))
      }
      nodeBuilder.setCanNavigate((legacyNode as? Navigatable)?.canNavigate() == true)
      nodeBuilder.setCanNavigateToSource((legacyNode as? Navigatable)?.canNavigateToSource() == true)
      nodeBuilder.setIncludedInExpandAll(legacyNode.isIncludedInExpandAll)
      // Only used to suppress error stripes of expanded nodes, which is what ScopeViewTreeModel.getStripe()
      // does for every one of its own nodes, all of which are directory-ish.
      nodeBuilder.setIsDirectory((legacyNode as? ProjectViewNode<*>)?.virtualFile?.isDirectory == true)
      nodeBuilder.setExpandOnDoubleClick(legacyNode.expandOnDoubleClick())
    }
  }

  private fun isValid(legacyNode: AbstractTreeNode<*>): Boolean = legacyNode.value != null
}

/** A [TreeStructureProjectViewNode] backed by a legacy node of [ScopeViewTreeModel]. */
private data class ScopeViewNode(
  override val elementDescriptor: AbstractTreeNode<*>,
) : TreeStructureProjectViewNode

private val TreeStructureProjectViewNode.legacyNode: AbstractTreeNode<*>
  get() = (this as ScopeViewNode).elementDescriptor

/** The same as `TreeStructureProjectViewNodeProvider`'s comparator: the legacy one, reading the new settings. */
private class ScopeViewComparator(
  project: Project,
  private val settings: ProjectViewPaneSettingsAccessor,
) : GroupByTypeComparator(project, null) {

  override fun getSortKey(): NodeSortKey = settings.getSortKey().toLegacySortKey()

  override fun isManualOrder(): Boolean = settings.isOptionSelected(ProjectViewPaneOption.ManualOrder)

  override fun isAbbreviateQualifiedNames(): Boolean = settings.isOptionSelected(ProjectViewPaneOption.AbbreviatePackageNames)

  override fun isFoldersAlwaysOnTop(): Boolean = settings.isOptionSelected(ProjectViewPaneOption.FoldersAlwaysOnTop)
}
