// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManagerListener
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.platform.kernel.withKernel
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update.Companion.create
import com.intellij.vcs.impl.backend.shelf.diff.BackendShelveEditorDiffPreview
import com.intellij.vcs.impl.shared.rhizome.NodeEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvesTreeRootEntity
import com.intellij.vcs.impl.shared.rpc.ChangeListDto
import fleet.kernel.SharedRef
import fleet.kernel.change
import fleet.kernel.shared
import fleet.kernel.sharedRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class ShelfTreeHolder(project: Project, val cs: CoroutineScope) : Disposable {

  init {
    project.messageBus.connect(this).subscribe(ShelveChangesManager.SHELF_TOPIC, ShelveChangesManagerListener { scheduleTreeUpdate() })
  }

  private val updateQueue = MergingUpdateQueue("Update Shelf Content", 200, true, null, project, null, true)

  private val tree = ShelfTree(project)

  private val diffPreview = BackendShelveEditorDiffPreview(tree)

  private fun updateTreeModel() {
    tree.invalidateDataAndRefresh {
      saveModelToDb()
    }
  }

  private fun saveModelToDb() {
    cs.launch {
      val root = tree.model.root as ChangesBrowserNode<*>
      withKernel {
        val rootEntity = ShelvesTreeRootEntity.single()
        var order = 0
        for (child in root.children()) {
          val nodeEntity = dfs(child as ChangesBrowserNode<*>, null, order++) ?: continue
          change {
            shared {
              rootEntity.add(NodeEntity.Children, nodeEntity)
            }
          }
        }
      }
    }
  }

  private suspend fun dfs(node: ChangesBrowserNode<*>, parent: NodeEntity?, orderInParent: Int): NodeEntity? {
    val entity = node.save(orderInParent) ?: return null
    if (parent != null) {
      change {
        shared {
          parent.add(NodeEntity.Children, entity)
        }
      }
    }
    var order = 0
    for (child in node.children()) {
      dfs(child as ChangesBrowserNode<*>, entity, order++)
    }
    return entity
  }

  private fun ChangesBrowserNode<*>.save(orderInParent: Int): NodeEntity? {
    val entity = this.convertToEntity(orderInParent) ?: return null
    putUserData(ENTITY_ID_KEY, entity.sharedRef())
    return entity
  }

  fun showDiff(changeListDto: ChangeListDto) {
    val changeListNode = TreeUtil.treeTraverser(tree)
                           .bfsTraversal()
                           .find { (it as ChangesBrowserNode<*>).getUserData(ENTITY_ID_KEY) == changeListDto.changeList } as? ChangesBrowserNode<*>
                         ?: return
    val selectedChanges = changeListNode.traverse().filter(ShelvedChangeNode::class.java).filter {
      val changeRef = it.getUserData(ENTITY_ID_KEY) as? SharedRef<*> ?: return@filter false
      return@filter changeListDto.changes.contains(changeRef)
    }.map { it.shelvedChange }.toList()

    tree.selectedChanges = selectedChanges
    cs.launch(Dispatchers.EDT) {
      diffPreview.performDiffAction()
    }
  }

  fun scheduleTreeUpdate() {
    updateQueue.queue(create("update") { updateTreeModel() })
  }

  override fun dispose() {
    updateQueue.cancelAllUpdates()
  }

  companion object {
    val ENTITY_ID_KEY = Key<SharedRef<NodeEntity>>("persistentId")

    fun getInstance(project: Project) = project.service<ShelfTreeHolder>()
  }
}
