// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.vcs.impl.frontend.shelf

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.asEntity
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.vcs.impl.frontend.changes.ChangeList
import com.intellij.vcs.impl.shared.rhizome.DiffSplitterEntity
import com.intellij.vcs.impl.shared.rhizome.SelectShelveChangeEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import com.intellij.vcs.impl.shared.rpc.ChangeListDto
import com.intellij.vcs.impl.shared.rpc.RemoteShelfActionsApi
import com.intellij.vcs.impl.shared.rpc.RemoteShelfApi
import com.jetbrains.rhizomedb.entity
import fleet.kernel.change
import fleet.kernel.ref
import fleet.kernel.rete.collectLatest
import fleet.kernel.rete.each
import fleet.kernel.rete.filter
import fleet.kernel.shared
import fleet.kernel.DurableRef
import fleet.rpc.remoteApiDescriptor
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
        val projectRef = project.asEntity().ref()
        RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfActionsApi>()).unshelve(projectRef, changeLists, withDialog)
      }
    }
  }

  fun deleteChangeList(changeLists: Set<ShelvedChangeListEntity>, changes: Map<ShelvedChangeListEntity, List<ShelvedChangeEntity>>) {
    cs.launch(Dispatchers.IO) {
      withKernel {
        val exactlySelectedLists = changeLists.map {
          it.ref()
        }

        val projectRef = project.asEntity().ref()
        RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfActionsApi>()).delete(projectRef, exactlySelectedLists, changes.toDtos())

      }
    }
  }

  fun compareWithLocal(changeListsMap: Map<ShelvedChangeListEntity, List<ShelvedChangeEntity>>, withLocal: Boolean) {
    cs.launch(Dispatchers.IO) {
      withKernel {
        val changeLists = changeListsMap.toDtos()
        val projectRef = project.asEntity().ref()
        RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfActionsApi>()).showStandaloneDiff(projectRef, changeLists, withLocal)
      }
    }
  }

  private fun Map<ShelvedChangeListEntity, List<ShelvedChangeEntity>>.toDtos(): List<ChangeListDto> = map {
    ChangeListDto(it.key.ref(), it.value.map { it.ref() })
  }

  fun renameChangeList(changeList: ShelvedChangeListEntity, newName: String) {
    cs.launch(Dispatchers.IO) {
      withKernel {
        val projectRef = project.asEntity().ref()
        RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfApi>()).renameShelvedChangeList(projectRef, changeList.ref(), newName)
        change {
          changeList[ShelvedChangeListEntity.Description] = newName
        }
      }
    }
  }

  fun importPatches() {
    cs.launch(Dispatchers.IO) {
      withKernel {
        val projectRef = project.asEntity().ref()
        RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfActionsApi>()).importShelvesFromPatches(projectRef)
      }
    }
  }

  fun navigateToSource(lists: Map<ShelvedChangeListEntity, List<ShelvedChangeEntity>>, focusEditor: Boolean) {
    cs.launch(Dispatchers.IO) {
      withKernel {
        val changeLists = lists.map {
          ChangeListDto(changeList = it.key.ref(), changes = it.value.map { it.ref() })
        }
        val projectRef = project.asEntity().ref()
        RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfActionsApi>()).navigateToSource(projectRef, changeLists, focusEditor)
      }
    }
  }

  fun createPatch(changeLists: List<ChangeList>, silentClipboard: Boolean) {
    cs.launch(Dispatchers.IO) {
      withKernel {
        val projectRef = project.asEntity().ref()
        val changeLists = changeLists.map {
          val changeListNode = it.changeListNode as ShelvedChangeListEntity
          val changes = it.changes.map { (it as ShelvedChangeEntity).ref() }
          ChangeListDto(changeListNode.ref(), changes)
        }
        RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfActionsApi>()).createPatchForShelvedChanges(projectRef, changeLists, silentClipboard)
      }
    }
  }

  fun restoreShelves(deletedChangeLists: Set<ShelvedChangeListEntity>) {
    cs.launch(Dispatchers.IO) {
      withKernel {
        val changelistRefs = deletedChangeLists.map { it.ref() }
        RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfActionsApi>()).restoreShelves(project.asEntity().ref(), changelistRefs)
      }
    }
  }

  fun createPreviewDiffSplitter() {
    cs.launch(Dispatchers.IO) {
      withKernel {
        RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfActionsApi>()).createPreviewDiffSplitter(project.asEntity().ref())
      }
    }
  }

  fun deleteSplitterPreview() {
    cs.launch {
      withKernel {
        change {
          shared {
            entity(DiffSplitterEntity.Project, project.asEntity())?.delete()
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
      SelectShelveChangeEntity.each().filter { entity -> entity.project == project.asEntity() }.collectLatest { listener(it) }
    }
  }
}

fun subscribeToDiffPreviewChanged(project: Project, cs: CoroutineScope, listener: (DiffSplitterEntity) -> Unit) {
  cs.launch {
    withKernel {
      DiffSplitterEntity.each().filter { entity -> entity.project == project.asEntity() }.collectLatest { listener(it) }
    }
  }
}