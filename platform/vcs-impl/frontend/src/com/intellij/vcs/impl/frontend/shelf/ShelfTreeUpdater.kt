// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.asEntity
import com.intellij.ui.content.Content
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.impl.frontend.changes.ChangesTreeModel
import com.intellij.vcs.impl.frontend.shelf.tree.ShelfTree
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvesTreeRootEntity
import com.intellij.vcs.impl.shared.rpc.ChangeListDto
import com.intellij.vcs.impl.shared.rpc.RemoteShelfApi
import fleet.kernel.ref
import fleet.kernel.rete.collectLatest
import fleet.kernel.rete.each
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultMutableTreeNode

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ShelfTreeUpdater(private val project: Project, private val cs: CoroutineScope) {
  private val tree = ShelfTree(project, cs)

  init {
    subscribeToTreeChanges()
    cs.launch(Dispatchers.IO) {
      withKernel {
        RemoteShelfApi.getInstance().loadChangesAsync(project.asEntity().ref())
      }
    }
  }

  fun createToolWindowPanel(): ShelfToolWindowPanel {
    return ShelfToolWindowPanel(project, tree, cs)
  }

  private fun subscribeToTreeChanges() {
    cs.launch {
      withKernel {
        ShelvesTreeRootEntity.each().collectLatest {
          val changes = tree.getSelectedChangesWithChangeLists()
          val rootNode = it.convertToTreeNodeRecursive() ?: return@collectLatest
          withContext(Dispatchers.EDT) {
            tree.model = ChangesTreeModel(rootNode)
            selectChangesInTree(changes)
          }
        }
      }
    }
  }

  suspend fun selectChangesInTree(changes: Map<ShelvedChangeListEntity, List<ShelvedChangeEntity>>) {
    withKernel {
      val firstChangeList = changes.entries.firstOrNull()
      val list = firstChangeList?.key ?: return@withKernel
      val changeListNode = TreeUtil.findNode(tree.getRoot()) {
        val changeList = it.userObject as? ShelvedChangeListEntity ?: return@findNode false
        return@findNode changeList.name == list.name && changeList.date == list.date
      } ?: return@withKernel
      val changeNodes = firstChangeList.value.mapNotNull { oldChange ->
        TreeUtil.findNode(changeListNode) {
          val change = it.userObject as? ShelvedChangeEntity ?: return@findNode false
          return@findNode change.filePath == oldChange.filePath
        }
      }
      val pathsToSelect = changeNodes.map { TreeUtil.getPathFromRoot(it) }
      TreeUtil.selectPaths(tree, pathsToSelect)

      notifyNodesSelected(changeListNode, changeNodes)
    }
  }

  private fun notifyNodesSelected(changeListNode: DefaultMutableTreeNode, changeNodes: List<DefaultMutableTreeNode>) {
    cs.launch(Dispatchers.IO) {
      withKernel {
        val changeListEntity = changeListNode.userObject as ShelvedChangeListEntity
        val dto = ChangeListDto(changeListEntity.ref(), changeNodes.map { it.userObject as ShelvedChangeEntity }.map { it.ref() })
        RemoteShelfApi.getInstance().notifyNodeSelected(project.asEntity().ref(), dto, false)
      }
    }
  }

  private fun selectShelvedList(list: ShelvedChangeListEntity) {
    val treeNode = TreeUtil.findNodeWithObject(tree.model.root as DefaultMutableTreeNode, list)
    if (treeNode == null) {
      return
    }
    TreeUtil.selectNode(tree, treeNode)
  }

  fun startEditing(list: ShelvedChangeListEntity) {
    selectShelvedList(list)
    tree.startEditingAtPath(tree.leadSelectionPath)
  }

  companion object {
    fun getInstance(project: Project): ShelfTreeUpdater = project.getService(ShelfTreeUpdater::class.java)

    val CONTENT_PROVIDER_SUPPLIER_KEY = Key.create<() -> ShelfToolWindowPanel?>("CONTENT_PROVIDER_SUPPLIER")
  }
}