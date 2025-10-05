// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.shelf

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
import com.intellij.platform.project.asEntity
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.shelf.diff.BackendShelveEditorDiffPreview
import com.intellij.vcs.shelf.diff.ShelfDiffChangesProvider
import com.intellij.vcs.shelf.diff.ShelfDiffChangesState
import com.intellij.vcs.shelf.diff.ShelvedPreviewProcessor
import com.intellij.platform.vcs.impl.shared.changes.GroupingUpdatePlaces
import com.intellij.platform.vcs.impl.shared.changes.PreviewDiffSplitterComponent
import com.intellij.platform.vcs.impl.shared.rhizome.DiffSplitterEntity
import com.intellij.platform.vcs.impl.shared.rhizome.GroupingItemEntity
import com.intellij.platform.vcs.impl.shared.rhizome.GroupingItemsEntity
import com.intellij.platform.vcs.impl.shared.rhizome.NodeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.SelectShelveChangeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ShelfTreeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvesTreeRootEntity
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListRpc
import com.jetbrains.rhizomedb.entity
import fleet.kernel.*
import fleet.kernel.rete.Rete
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultTreeModel
import kotlin.collections.iterator

private const val UPDATE_DEBOUNCE_TIMEOUT = 200L

@ApiStatus.Internal
@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
class ShelfTreeHolder(val project: Project, val cs: CoroutineScope) {

  private val postUpdateActivities = mutableListOf<suspend () -> Unit>()
  private val updateFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    cs.launch {
      updateFlow.debounce(UPDATE_DEBOUNCE_TIMEOUT).collectLatest {
        model = buildTreeModelSync()
        diffChangesProvider.treeModel = model
        val activities = postUpdateActivities.toList()
        updateDbModel()
        withContext(NonCancellable) {
          activities.forEach { it() }
          postUpdateActivities.removeAll(activities)
        }
      }
    }
    project.messageBus.connect(cs).subscribe(ShelveChangesManager.SHELF_TOPIC, ShelveChangesManagerListener { scheduleTreeUpdate() })
  }

  private val shelveChangesManager = ShelveChangesManager.getInstance(project)

  private var grouping: ChangesGroupingSupport = ChangesGroupingSupport(project, this, false)

  private var model = buildTreeModelSync(true)

  private val diffChangesProvider = ShelfDiffChangesProvider(model)
  private val diffPreview = BackendShelveEditorDiffPreview(project, cs, diffChangesProvider)

  suspend fun createPreviewDiffSplitter() {
    withContext(Dispatchers.EDT) {
      val processor = ShelvedPreviewProcessor(project, cs, false, diffChangesProvider)
      val previewDiffSplitterComponent = PreviewDiffSplitterComponent(processor, SHELVE_PREVIEW_SPLITTER_PROPORTION)
      val projectEntity = project.asEntity()
      change {
        DiffSplitterEntity.upsert(DiffSplitterEntity.Project, projectEntity) {
          it[DiffSplitterEntity.Splitter] = previewDiffSplitterComponent
        }.onDispose(coroutineContext[Rete]!!) { Disposer.dispose(processor) }
      }
    }
  }

  private suspend fun updateDbModel() {
    val root = model.root as ChangesBrowserNode<*>
    val projectEntity = project.asEntity()
    val rootEntity = change {
      shared {
        entity(ShelfTreeEntity.Project, projectEntity)?.delete()
        ShelvesTreeRootEntity.new {
          it[NodeEntity.Order] = 0
        }
      }
    }
    try {
      var order = 0
      val rootNodes = mutableSetOf<NodeEntity>()
      for (child in root.children()) {
        val entity = dfs(child as ChangesBrowserNode<*>, rootEntity, order++) ?: continue
        rootNodes.add(entity)
      }
      createTreeEntity(project, rootEntity)
    }
    catch (e: Exception) {
      withContext(NonCancellable) {
        change {
          shared {
            rootEntity.delete()
          }
        }
        throw e
      }
    }
  }

  private suspend fun dfs(node: ChangesBrowserNode<*>, parent: NodeEntity, orderInParent: Int): NodeEntity? {
    val entity = change {
      shared {
        val childNode = save(node, orderInParent, project) ?: return@shared null
        parent.add(NodeEntity.Children, childNode)
        return@shared childNode
      }
    } ?: return null

    var order = 0
    for (child in node.children()) {
      dfs(child as ChangesBrowserNode<*>, entity, order++)
    }
    return entity
  }

  private fun SharedChangeScope.save(node: ChangesBrowserNode<*>, orderInParent: Int, project: Project): NodeEntity? {
    val entity = this.convertToEntity(node, orderInParent, project) ?: return null
    node.putUserData(ENTITY_ID_KEY, entity.ref())
    return entity
  }

  private suspend fun createTreeEntity(project: Project, rootEntity: ShelvesTreeRootEntity): ShelfTreeEntity {
    val projectEntity = project.asEntity()
    return change {
      shared {
        ShelfTreeEntity.new {
          it[ShelfTreeEntity.Project] = projectEntity
          it[ShelfTreeEntity.Root] = rootEntity
        }
      }
    }
  }

  internal fun findChangesInTree(changeListRpc: ChangeListRpc): List<ShelvedChangeNode> {
    val changeListNode = findChangeListNode(changeListRpc.changeList)
                         ?: return emptyList()
    val selectedChanges = changeListNode.traverse().filterIsInstance<ShelvedChangeNode>().filter {
      val changeRef = it.getUserData(ENTITY_ID_KEY) as? DurableRef<*> ?: return@filter false
      return@filter changeListRpc.changes.isEmpty() || changeListRpc.changes.contains(changeRef)
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

  suspend fun showDiff(changeListRpc: ChangeListRpc) {
    val selectedChanges = findChangesInTree(changeListRpc)

    val changesState = ShelfDiffChangesState(selectedChanges.map { it.shelvedChange })
    diffChangesProvider.changesStateFlow.tryEmit(changesState)
    withContext(Dispatchers.EDT) {
      diffPreview.performDiffAction()
    }
  }

  fun scheduleTreeUpdate(activity: suspend () -> Unit = {}) {
    postUpdateActivities.add(activity)
    updateFlow.tryEmit(Unit)
  }

  suspend fun saveGroupings() {
    GroupingPolicyFactoryHolder.getInstance().saveGroupingKeysToDb()
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

  suspend fun selectChangeListInTree(changeList: ShelvedChangeList) {
    val nodeToSelect = TreeUtil.findNodeWithObject(model.root as ChangesBrowserNode<*>, changeList) as? ChangesBrowserNode<*>
                       ?: return
    val nodeRef = nodeToSelect.getUserData(ENTITY_ID_KEY) ?: return
    val projectEntity = project.asEntity()
    change {
      shared {
        val changeListEntity = nodeRef.deref() as? ShelvedChangeListEntity ?: return@shared
        SelectShelveChangeEntity.new {
          it[SelectShelveChangeEntity.ChangeList] = changeListEntity
          it[SelectShelveChangeEntity.Project] = projectEntity
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


  suspend fun changeGrouping(groupingKeys: Set<String>) {
    grouping.setGroupingKeys(groupingKeys)
    val deferred = CompletableDeferred<Unit>()
    scheduleTreeUpdate { deferred.complete(Unit) }
    deferred.await()
  }

  fun updateDiffFile(changeListRpc: ChangeListRpc, fromModelChange: Boolean) {
    val selectedChanges = findChangesInTree(changeListRpc).map { it.shelvedChange }
    diffChangesProvider.changesStateFlow.tryEmit(ShelfDiffChangesState(selectedChanges, fromModelChange))
  }

  fun isDirectoryGroupingEnabled(): Boolean {
    return grouping.isDirectory
  }

  companion object {
    private const val SHELVE_PREVIEW_SPLITTER_PROPORTION = "ShelvedChangesViewManager.DETAILS_SPLITTER_PROPORTION"
    val ENTITY_ID_KEY: Key<DurableRef<NodeEntity>> = Key<DurableRef<NodeEntity>>("persistentId")

    fun getInstance(project: Project): ShelfTreeHolder = project.service<ShelfTreeHolder>()
  }
}
