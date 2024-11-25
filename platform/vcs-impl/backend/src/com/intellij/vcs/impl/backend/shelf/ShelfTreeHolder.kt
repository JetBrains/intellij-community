// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManagerListener
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport
import com.intellij.openapi.vcs.changes.ui.GroupingPolicyFactoryHolder
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.asEntity
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update.Companion.create
import com.intellij.vcs.impl.backend.shelf.diff.BackendShelveEditorDiffPreview
import com.intellij.vcs.impl.backend.shelf.diff.ShelfDiffChangesProvider
import com.intellij.vcs.impl.backend.shelf.diff.ShelfDiffChangesState
import com.intellij.vcs.impl.backend.shelf.diff.ShelvedPreviewProcessor
import com.intellij.vcs.impl.shared.changes.GroupingUpdatePlaces
import com.intellij.vcs.impl.shared.changes.PreviewDiffSplitterComponent
import com.intellij.vcs.impl.shared.rhizome.*
import com.intellij.vcs.impl.shared.rpc.ChangeListDto
import com.jetbrains.rhizomedb.entity
import fleet.kernel.*
import fleet.kernel.rete.Rete
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultTreeModel

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ShelfTreeHolder(val project: Project, val cs: CoroutineScope) : Disposable {

  init {
    project.messageBus.connect(this).subscribe(ShelveChangesManager.SHELF_TOPIC, ShelveChangesManagerListener { scheduleTreeUpdate() })
  }

  private val updateQueue = MergingUpdateQueue("Update Shelf Content", 200, true, null, project, null, true)

  private val shelveChangesManager = ShelveChangesManager.getInstance(project)

  private var grouping: ChangesGroupingSupport = ChangesGroupingSupport(project, this, false)

  private var model = buildTreeModelSync(true)

  private val diffChangesProvider = ShelfDiffChangesProvider(model)
  private val diffPreview = BackendShelveEditorDiffPreview(project, cs, diffChangesProvider)

  fun createPreviewDiffSplitter() {
    cs.launch(Dispatchers.EDT) {
      val processor = ShelvedPreviewProcessor(project, cs, false, diffChangesProvider)
      val previewDiffSplitterComponent = PreviewDiffSplitterComponent(processor, SHELVE_PREVIEW_SPLITTER_PROPORTION)
      withKernel {
        change {
          DiffSplitterEntity.upsert(DiffSplitterEntity.Project, project.asEntity()) {
            it[DiffSplitterEntity.Splitter] = previewDiffSplitterComponent
          }.onDispose(coroutineContext[Rete]!!) { Disposer.dispose(processor) }
        }
      }
    }
  }

  fun updateDbModel(afterUpdate: () -> Unit = {}) {
    cs.launch {
      val root = model.root as ChangesBrowserNode<*>
      withKernel {
        var order = 0
        val rootNodes = mutableSetOf<NodeEntity>()
        for (child in root.children()) {
          val entity = dfs(child as ChangesBrowserNode<*>, null, order++) ?: continue
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

  private suspend fun dfs(node: ChangesBrowserNode<*>, parent: NodeEntity?, orderInParent: Int): NodeEntity? {
    val entity = node.save(orderInParent, project) ?: return null
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

  private suspend fun ChangesBrowserNode<*>.save(orderInParent: Int, project: Project): NodeEntity? {
    val entity = this.convertToEntity(orderInParent, project) ?: return null
    putUserData(ENTITY_ID_KEY, entity.ref())
    return entity
  }

  internal fun findChangesInTree(changeListDto: ChangeListDto): List<ShelvedChangeNode> {
    val changeListNode = findChangeListNode(changeListDto.changeList)
                         ?: return emptyList()
    val selectedChanges = changeListNode.traverse().filter(ShelvedChangeNode::class.java).filter {
      val changeRef = it.getUserData(ENTITY_ID_KEY) as? DurableRef<*> ?: return@filter false
      return@filter changeListDto.changes.isEmpty() || changeListDto.changes.contains(changeRef)
    }
    return selectedChanges.toList()
  }

  internal fun findChangeListNode(changeList: DurableRef<ShelvedChangeListEntity>): ChangesBrowserNode<*>? {
    return TreeUtil.modelTraverser(model)
      .bfsTraversal()
      .find { (it as ChangesBrowserNode<*>).getUserData(ENTITY_ID_KEY) == changeList } as? ChangesBrowserNode<*>
  }

  fun renameChangeList(changeList: DurableRef<ShelvedChangeListEntity>, newName: String) {
    val changeListNode = findChangeListNode(changeList) as? ShelvedListNode ?: return
    changeListNode.changeList.description = newName
  }

  fun showDiff(changeListDto: ChangeListDto) {
    val selectedChanges = findChangesInTree(changeListDto)

    val changesState = ShelfDiffChangesState(selectedChanges.map { it.shelvedChange })
    diffChangesProvider.changesStateFlow.tryEmit(changesState)
    cs.launch(Dispatchers.EDT) {
      diffPreview.performDiffAction()
    }
  }

  fun scheduleTreeUpdate(callback: () -> Unit = {}) {
    updateQueue.queue(create("update") {
      model = buildTreeModelSync()
      diffChangesProvider.treeModel = model
      updateDbModel(callback)
    })
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
      val nodeToSelect = TreeUtil.findNodeWithObject(model.root as ChangesBrowserNode<*>, changeList) as? ChangesBrowserNode<*>
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

  fun buildTreeModelSync(init: Boolean = false): DefaultTreeModel {
    val showRecycled = ShelveChangesManager.getInstance(project).isShowRecycled
    if (init) {
      grouping.setGroupingKeysOrSkip(ShelveChangesManager.getInstance(project).grouping)
    }
    val modelBuilder = ShelvedTreeModelBuilder(project, grouping.grouping)
    val changeLists = ShelveChangesManager.getInstance(project).allLists
    modelBuilder.setShelvedLists(changeLists.filter { !it.isDeleted && (showRecycled || !it.isRecycled) })
    modelBuilder.setDeletedShelvedLists(changeLists.filter { it.isDeleted })
    return modelBuilder.build()
  }


  fun changeGrouping(groupingKeys: Set<String>) {
    grouping.setGroupingKeys(groupingKeys)
    scheduleTreeUpdate()
  }

  fun updateDiffFile(changeListDto: ChangeListDto, fromModelChange: Boolean) {
    val selectedChanges = findChangesInTree(changeListDto).map { it.shelvedChange }
    diffChangesProvider.changesStateFlow.tryEmit(ShelfDiffChangesState(selectedChanges, fromModelChange))
  }

  fun isDirectoryGroupingEnabled(): Boolean {
    return grouping.isDirectory
  }


  override fun dispose() {
    updateQueue.cancelAllUpdates()
  }

  companion object {
    private const val SHELVE_PREVIEW_SPLITTER_PROPORTION = "ShelvedChangesViewManager.DETAILS_SPLITTER_PROPORTION"
    val ENTITY_ID_KEY: Key<DurableRef<NodeEntity>> = Key<DurableRef<NodeEntity>>("persistentId")

    fun getInstance(project: Project): ShelfTreeHolder = project.service<ShelfTreeHolder>()
  }
}
