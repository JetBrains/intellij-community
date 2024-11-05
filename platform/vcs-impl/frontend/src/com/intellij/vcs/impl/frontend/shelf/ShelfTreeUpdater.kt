// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.asEntity
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.ui.content.Content
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.impl.frontend.changes.ChangesTreeModel
import com.intellij.vcs.impl.frontend.shelf.tree.ChangesBrowserRootNode
import com.intellij.vcs.impl.frontend.shelf.tree.ShelfTree
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvesTreeRootEntity
import com.intellij.vcs.impl.shared.rpc.RemoteShelfApi
import com.jetbrains.rhizomedb.entity
import fleet.kernel.rete.collectLatest
import fleet.kernel.rete.each
import fleet.kernel.DurableRef
import fleet.kernel.ref
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfApi>()).loadChangesAsync(project.asEntity().ref())
      }
    }
  }

  fun initContent(content: Content) {
    val toolWindowPanel = content.getUserData(CONTENT_PROVIDER_SUPPLIER_KEY)?.invoke() ?: return
    content.putUserData(CONTENT_PROVIDER_SUPPLIER_KEY, null)
    content.component = toolWindowPanel
  }

  fun initToolWindowPanel(): ShelfToolWindowPanel {
    return ShelfToolWindowPanel(project, tree, cs)
  }

  private suspend fun findRootEntity(): ShelvesTreeRootEntity? {
    return withKernel {
      entity(ShelvesTreeRootEntity.Project, project.asEntity())
    }
  }

  private fun subscribeToTreeChanges() {
    cs.launch {
      withKernel {
        ShelvesTreeRootEntity.each().collectLatest {
          val rootNode = it.convertToTreeNodeRecursive() ?: return@collectLatest
          cs.launch(Dispatchers.EDT) {
            tree.model = ChangesTreeModel(rootNode)
          }
        }
      }
    }
  }

  fun updateTree(rootEntity: ShelvesTreeRootEntity) {
    cs.launch {
      withKernel {
        val rootNode = rootEntity.convertToTreeNodeRecursive() ?: return@withKernel
        cs.launch(Dispatchers.EDT) {
          tree.updateModel(ChangesTreeModel(rootNode))
        }
      }
    }
  }


  fun selectShelvedList(list: ShelvedChangeListEntity) {
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