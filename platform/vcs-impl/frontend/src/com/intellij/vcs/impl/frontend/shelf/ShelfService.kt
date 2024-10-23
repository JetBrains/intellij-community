// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.asEntity
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.vcs.impl.frontend.changes.ChangeList
import com.intellij.vcs.impl.shared.rhizome.SelectShelveChangeEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import com.intellij.vcs.impl.shared.rpc.ChangeListDto
import com.intellij.vcs.impl.shared.rpc.RemoteShelfActionsApi
import com.intellij.vcs.impl.shared.rpc.RemoteShelfApi
import fleet.kernel.rete.collectLatest
import fleet.kernel.rete.each
import fleet.kernel.rete.filter
import fleet.kernel.sharedRef
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class ShelfService(private val project: Project, private val cs: CoroutineScope) {
  fun unshelveSilently(changeListsMap: Map<ShelvedChangeListEntity, List<ShelvedChangeEntity>>?) {
    cs.launch {
      withKernel {
        val changeLists = changeListsMap?.map {
          ChangeListDto(it.key.sharedRef(), it.value.map { it.sharedRef() })
        } ?: return@withKernel
        val projectRef = project.asEntity().sharedRef()
        RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfActionsApi>()).unshelveSilently(projectRef, changeLists)
      }
    }
  }

  fun createPatch(changeLists: List<ChangeList>, silentClipboard: Boolean) {
    cs.launch {
      withKernel {
        val projectRef = project.asEntity().sharedRef()
        val changeLists = changeLists.map {
          val changeListNode = it.changeListNode as ShelvedChangeListEntity
          val changes = it.changes.map { (it as ShelvedChangeEntity).sharedRef() }
          ChangeListDto(changeListNode.sharedRef(), changes)
        }
        RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfActionsApi>()).createPatchForShelvedChanges(projectRef, changeLists, silentClipboard)
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