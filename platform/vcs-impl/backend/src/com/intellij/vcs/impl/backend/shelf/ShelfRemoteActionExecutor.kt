// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction
import com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFile
import com.intellij.openapi.vcs.changes.shelf.ShelvedChange
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.vcs.impl.shared.rpc.ChangeListDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class ShelfRemoteActionExecutor(private val project: Project, private val cs: CoroutineScope) {
  private val shelfTreeHolder = ShelfTreeHolder.getInstance(project)

  fun createPatchForShelvedChanges(changeListsDto: List<ChangeListDto>, silentClipboard: Boolean) {
    cs.launch(Dispatchers.EDT) {
      val patchBuilder: CreatePatchCommitExecutor.PatchBuilder
      val changeNodes = changeListsDto.flatMap { shelfTreeHolder.findChangesInTree(it) }
      val changeList = changeNodes.first().shelvedChange.changeList
      if (changeListsDto.size == 1) {
        patchBuilder = CreatePatchCommitExecutor.ShelfPatchBuilder(project, changeList, changeNodes.map { it.shelvedChange.path })
      }
      else {
        patchBuilder = CreatePatchCommitExecutor.DefaultPatchBuilder(project)
      }
      val changes = changeNodes.map { it.shelvedChange.getChangeWithLocal(project) }
      CreatePatchFromChangesAction.createPatch(project, changeList.description, changes, silentClipboard, patchBuilder)
    }
  }

  fun unshelveSilently(changeListDto: List<ChangeListDto>) {
    cs.launch {
      withContext(Dispatchers.EDT) {
        FileDocumentManager.getInstance().saveAllDocuments()
      }
      val changeLists = mutableListOf<ShelvedChangeList>()
      val changes = mutableListOf<ShelvedChange>()
      val files = mutableListOf<ShelvedBinaryFile>()
      changeListDto.forEach {
        shelfTreeHolder.findChangesInTree(it).forEach { node ->
          val change = node.shelvedChange
          changeLists.add(change.changeList)
          if (change.binaryFile != null) {
            files.add(change.binaryFile!!)
          }
          else {
            changes.add(change.shelvedChange!!)
          }
        }
      }

      ShelveChangesManager.getInstance(project).unshelveSilentlyAsynchronously(project, changeLists, changes, files, null)
    }
  }

  companion object {
    fun getInstance(project: Project): ShelfRemoteActionExecutor = project.service<ShelfRemoteActionExecutor>()
  }

}