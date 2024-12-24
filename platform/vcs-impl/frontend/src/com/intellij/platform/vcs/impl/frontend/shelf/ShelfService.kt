// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.platform.vcs.impl.frontend.shelf

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.asEntity
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.frontend.changes.ChangeList
import com.intellij.platform.vcs.impl.shared.rhizome.DiffSplitterEntity
import com.intellij.platform.vcs.impl.shared.rhizome.SelectShelveChangeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListRpc
import com.intellij.platform.vcs.impl.shared.rpc.RemoteShelfActionsApi
import com.intellij.platform.vcs.impl.shared.rpc.RemoteShelfApi
import com.jetbrains.rhizomedb.entity
import fleet.kernel.change
import fleet.kernel.ref
import fleet.kernel.rete.collectLatest
import fleet.kernel.rete.each
import fleet.kernel.rete.filter
import fleet.kernel.shared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ShelfService(private val project: Project, private val cs: CoroutineScope) {
  fun unshelve(changeListsMap: Map<ShelvedChangeListEntity, List<ShelvedChangeEntity>>, withDialog: Boolean) {
    cs.launch(Dispatchers.IO) {
      withKernel {
        val changeLists = changeListsMap.toDtos()
        RemoteShelfActionsApi.getInstance().unshelve(project.projectId(), changeLists, withDialog)
      }
    }
  }

  fun deleteChangeList(changeLists: Set<ShelvedChangeListEntity>, changes: Map<ShelvedChangeListEntity, List<ShelvedChangeEntity>>) {
    cs.launch(Dispatchers.IO) {
      withKernel {
        val exactlySelectedLists = changeLists.map {
          it.ref()
        }

        RemoteShelfActionsApi.getInstance().delete(project.projectId(), exactlySelectedLists, changes.toDtos())

      }
    }
  }

  fun compareWithLocal(changeListsMap: Map<ShelvedChangeListEntity, List<ShelvedChangeEntity>>, withLocal: Boolean) {
    cs.launch(Dispatchers.IO) {
      withKernel {
        val changeLists = changeListsMap.toDtos()
        RemoteShelfActionsApi.getInstance().showStandaloneDiff(project.projectId(), changeLists, withLocal)
      }
    }
  }

  private fun Map<ShelvedChangeListEntity, List<ShelvedChangeEntity>>.toDtos(): List<ChangeListRpc> = map {
    ChangeListRpc(it.key.ref(), it.value.map { it.ref() })
  }

  fun renameChangeList(changeList: ShelvedChangeListEntity, newName: String) {
    cs.launch(Dispatchers.IO) {
      withKernel {
        RemoteShelfApi.getInstance().renameShelvedChangeList(project.projectId(), changeList.ref(), newName)
        change {
          changeList[ShelvedChangeListEntity.Description] = newName
        }
      }
    }
  }

  fun importPatches() {
    cs.launch(Dispatchers.IO) {
      withKernel {
        RemoteShelfActionsApi.getInstance().importShelvesFromPatches(project.projectId())
      }
    }
  }

  fun navigateToSource(lists: Map<ShelvedChangeListEntity, List<ShelvedChangeEntity>>, focusEditor: Boolean) {
    cs.launch(Dispatchers.IO) {
      withKernel {
        val changeLists = lists.map {
          ChangeListRpc(changeList = it.key.ref(), changes = it.value.map { it.ref() })
        }
        RemoteShelfActionsApi.getInstance().navigateToSource(project.projectId(), changeLists, focusEditor)
      }
    }
  }

  fun createPatch(changeLists: List<ChangeList>, silentClipboard: Boolean) {
    cs.launch(Dispatchers.IO) {
      withKernel {
        val changeLists = changeLists.map {
          val changeListNode = it.changeListNode as ShelvedChangeListEntity
          val changes = it.changes.map { (it as ShelvedChangeEntity).ref() }
          ChangeListRpc(changeListNode.ref(), changes)
        }
        RemoteShelfActionsApi.getInstance().createPatchForShelvedChanges(project.projectId(), changeLists, silentClipboard)
      }
    }
  }

  fun restoreShelves(deletedChangeLists: Set<ShelvedChangeListEntity>) {
    cs.launch(Dispatchers.IO) {
      withKernel {
        val changelistRefs = deletedChangeLists.map { it.ref() }
        RemoteShelfActionsApi.getInstance().restoreShelves(project.projectId(), changelistRefs)
      }
    }
  }

  fun createPreviewDiffSplitter() {
    cs.launch(Dispatchers.IO) {
      withKernel {
        RemoteShelfActionsApi.getInstance().createPreviewDiffSplitter(project.projectId())
      }
    }
  }

  fun deleteSplitterPreview() {
    cs.launch {
      withKernel {
        val projectEntity = project.asEntity()
        change {
          shared {
            entity(DiffSplitterEntity.Project, projectEntity)?.delete()
          }
        }
      }
    }
  }

  companion object {
    fun getInstance(project: Project): ShelfService = project.getService(ShelfService::class.java)
  }
}


fun subscribeToShelfTreeSelectionChanged(project: Project, cs: CoroutineScope, listener: (SelectShelveChangeEntity) -> Unit) {
  cs.launch {
    withKernel {
      val projectEntity = project.asEntity()
      SelectShelveChangeEntity.each().filter { entity -> entity.project == projectEntity }.collectLatest { listener(it) }
    }
  }
}

fun subscribeToDiffPreviewChanged(project: Project, cs: CoroutineScope, listener: (DiffSplitterEntity) -> Unit) {
  cs.launch {
    withKernel {
      val projectEntity = project.asEntity()
      DiffSplitterEntity.each().filter { entity -> entity.project == projectEntity }.collectLatest { listener(it) }
    }
  }
}