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
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.GroupingPolicyFactoryHolder
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.asEntity
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update.Companion.create
import com.intellij.vcs.impl.backend.shelf.diff.BackendShelveEditorDiffPreview
import com.intellij.vcs.impl.shared.changes.GroupingUpdatePlaces
import com.intellij.vcs.impl.shared.rhizome.*
import com.intellij.vcs.impl.shared.rpc.ChangeListDto
import com.jetbrains.rhizomedb.entity
import fleet.kernel.SharedRef
import fleet.kernel.change
import fleet.kernel.shared
import fleet.kernel.sharedRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.tree.TreePath

@Service(Service.Level.PROJECT)
class ShelfTreeHolder(val project: Project, val cs: CoroutineScope) : Disposable {

  init {
    project.messageBus.connect(this).subscribe(ShelveChangesManager.SHELF_TOPIC, ShelveChangesManagerListener { scheduleTreeUpdate() })
  }

  private val updateQueue = MergingUpdateQueue("Update Shelf Content", 200, true, null, project, null, true)

  private val shelveChangesManager = ShelveChangesManager.getInstance(project)

  private val tree = ShelfTree(project)

  private val diffPreview = BackendShelveEditorDiffPreview(tree, cs, project)

  private fun updateTreeModel(afterUpdate: () -> Unit = {}) {
    tree.invalidateDataAndRefresh {
      updateDbModel(afterUpdate)
    }
  }

  fun updateDbModel(afterUpdate: () -> Unit = {}) {
    cs.launch {
      val root = tree.model.root as ChangesBrowserNode<*>
      withKernel {
        var order = 0
        val rootNodes = mutableSetOf<NodeEntity>()
        for (child in root.children()) {
          val entity = dfs(tree, child as ChangesBrowserNode<*>, null, order++) ?: continue
          rootNodes.add(entity)
        }
        change {
          shared {
            val projectEntity = project.asEntity()
            entity(ShelvesTreeRootEntity.Project, projectEntity)?.delete()
            ShelvesTreeRootEntity.new {
              it[NodeEntity.Order] = 0
              it[NodeEntity.Children] = rootNodes
              it[ShelvesTreeRootEntity.Project] = projectEntity
            }
          }
        }
      }
      afterUpdate()
    }
  }

  private suspend fun dfs(tree: ShelfTree, node: ChangesBrowserNode<*>, parent: NodeEntity?, orderInParent: Int): NodeEntity? {
    val entity = node.save(tree, orderInParent, project) ?: return null
    if (parent != null) {
      change {
        shared {
          parent.add(NodeEntity.Children, entity)
        }
      }
    }
    var order = 0
    for (child in node.children()) {
      dfs(this.tree, child as ChangesBrowserNode<*>, entity, order++)
    }
    return entity
  }

  private suspend fun ChangesBrowserNode<*>.save(tree: ShelfTree, orderInParent: Int, project: Project): NodeEntity? {
    val entity = this.convertToEntity(tree, orderInParent, project) ?: return null
    putUserData(ENTITY_ID_KEY, entity.sharedRef())
    return entity
  }

  internal fun findChangesInTree(changeListDto: ChangeListDto): List<ShelvedChangeNode> {
    val changeListNode = findChangeListNode(changeListDto.changeList)
                         ?: return emptyList()
    val selectedChanges = changeListNode.traverse().filter(ShelvedChangeNode::class.java).filter {
      val changeRef = it.getUserData(ENTITY_ID_KEY) as? SharedRef<*> ?: return@filter false
      return@filter changeListDto.changes.isEmpty() || changeListDto.changes.contains(changeRef)
    }
    return selectedChanges.toList()
  }

  internal fun findChangeListNode(changeList: SharedRef<ShelvedChangeListEntity>): ChangesBrowserNode<*>? {
    return TreeUtil.treeTraverser(tree)
      .bfsTraversal()
      .find { (it as ChangesBrowserNode<*>).getUserData(ENTITY_ID_KEY) == changeList } as? ChangesBrowserNode<*>
  }

  fun renameChangeList(changeList: SharedRef<ShelvedChangeListEntity>, newName: String) {
    val changeListNode = findChangeListNode(changeList) as? ShelvedListNode ?: return
    changeListNode.changeList.description = newName
  }

  fun showDiff(changeListDto: ChangeListDto) {
    val selectedChanges = findChangesInTree(changeListDto)

    tree.selectedChanges = selectedChanges.map { it.shelvedChange }
    cs.launch(Dispatchers.EDT) {
      diffPreview.performDiffAction()
    }
  }

  fun updateSelection(changeListDto: ChangeListDto) {
    val selectedChanges = findChangesInTree(changeListDto)
    tree.selectedChanges = selectedChanges.map { it.shelvedChange }
    cs.launch(Dispatchers.EDT) {
      tree.selectionModel.selectionPaths = selectedChanges.map { TreePath(it.path) }.toTypedArray() //wa to call TreeHandlerChangesTreeTracker.updatePreview()
    }
  }

  fun scheduleTreeUpdate(callback: () -> Unit = {}) {
    updateQueue.queue(create("update") { updateTreeModel(callback) })
  }

  fun saveGroupings() {
    cs.launch {
      GroupingPolicyFactoryHolder.getInstance().saveGroupingKeysToDb()
      withKernel {
        change {
          shared {
            val shelfTreeGroup = GroupingItemsEntity.new {
              it[GroupingItemsEntity.Place] = GroupingUpdatePlaces.SHELF_TREE
            }
            shelveChangesManager.grouping.forEach {
              val groupingItem = entity(GroupingItemEntity.Name, it) ?: return@forEach
              shelfTreeGroup.add(GroupingItemsEntity.Items, groupingItem)
            }
          }
        }
      }
    }
  }

  fun selectChangeListInTree(changeList: ShelvedChangeList) {
    cs.launch {
      val nodeToSelect = TreeUtil.findNodeWithObject(tree.model.root as ChangesBrowserNode<*>, changeList) as? ChangesBrowserNode<*>
                         ?: return@launch
      val nodeRef = nodeToSelect.getUserData(ENTITY_ID_KEY) ?: return@launch
      withKernel {
        change {
          shared {
            val changeListEntity = nodeRef.deref() as? ShelvedChangeListEntity ?: return@shared
            SelectShelveChangeEntity.new {
              it[SelectShelveChangeEntity.ChangeList] = changeListEntity
              it[SelectShelveChangeEntity.Project] = project.asEntity()
            }
          }
        }
      }
    }
  }


  fun changeGrouping(groupingKeys: Set<String>) {
    tree.groupingSupport.setGroupingKeys(groupingKeys)
  }

  override fun dispose() {
    updateQueue.cancelAllUpdates()
  }

  companion object {
    val ENTITY_ID_KEY = Key<SharedRef<NodeEntity>>("persistentId")
    const val REPOSITORY_GROUPING_KEY = "repository"

    fun getInstance(project: Project) = project.service<ShelfTreeHolder>()
  }
}
