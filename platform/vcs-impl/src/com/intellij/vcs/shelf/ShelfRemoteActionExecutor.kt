// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.shelf

import com.intellij.openapi.ListSelection
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.actions.CreatePatchFromChangesAction
import com.intellij.openapi.vcs.changes.patch.CreatePatchCommitExecutor
import com.intellij.openapi.vcs.changes.shelf.*
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListRpc
import com.intellij.pom.NavigatableAdapter
import com.intellij.util.OpenSourceUtil
import fleet.kernel.DurableRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

@Service(Service.Level.PROJECT)
internal class ShelfRemoteActionExecutor(private val project: Project, private val cs: CoroutineScope) {
  private val shelfTreeHolder = ShelfTreeHolder.getInstance(project)
  private val shelveChangesManager = ShelveChangesManager.getInstance(project)

  suspend fun createPatchForShelvedChanges(changeLists: List<ChangeListRpc>, silentClipboard: Boolean) {
    val patchBuilder: CreatePatchCommitExecutor.PatchBuilder
    val changeNodes = changeLists.flatMap { shelfTreeHolder.findChangesInTree(it) }
    val changeList = changeNodes.firstOrNull()?.shelvedChange?.changeList ?: return
    if (changeLists.size == 1) {
      patchBuilder = CreatePatchCommitExecutor.ShelfPatchBuilder(project, changeList, changeNodes.map { it.shelvedChange.path })
    }
    else {
      patchBuilder = CreatePatchCommitExecutor.DefaultPatchBuilder(project)
    }
    withContext(Dispatchers.EDT) {
      val changes = changeNodes.map { it.shelvedChange.getChangeWithLocal(project) }
      CreatePatchFromChangesAction.createPatch(project, changeList.description, changes, silentClipboard, patchBuilder)
    }
  }

  fun unshelve(changeListRpc: List<ChangeListRpc>, withDialog: Boolean) {
    cs.launch {
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          FileDocumentManager.getInstance().saveAllDocuments()
        }
      }

      val nodes = changeListRpc.flatMap {
        shelfTreeHolder.findChangesInTree(it)
      }
      val shelvedChanges = findChanges(nodes)
      val changeLists = shelvedChanges.changeLists
      cs.launch(Dispatchers.EDT) {
        if (withDialog) {
          if (changeLists.size == 1) {
            val changesToUnshelve = nodes.map { it.shelvedChange.getChangeWithLocal(project) }.toTypedArray()
            UnshelveWithDialogAction.unshelveSingleChangeList(changeLists.first(), project, changesToUnshelve)
          }
          else {
            UnshelveWithDialogAction.unshelveMultipleShelveChangeLists(project, changeLists, shelvedChanges.files, shelvedChanges.changes)
          }
          return@launch
        }
      }
      ShelveChangesManager.getInstance(project).unshelveSilentlyAsynchronously(project, changeLists, shelvedChanges.changes, shelvedChanges.files, null)
    }
  }

  private fun findChanges(
    nodes: List<ShelvedChangeNode>,
  ): ShelvedChanges {
    val changes = mutableListOf<ShelvedChange>()
    val files = mutableListOf<ShelvedBinaryFile>()
    val changeLists = mutableListOf<ShelvedChangeList>()

    nodes.forEach { node ->
      val change = node.shelvedChange
      changeLists.add(change.changeList)
      if (change.binaryFile != null) {
        files.add(change.binaryFile!!)
      }
      else {
        changes.add(change.shelvedChange!!)
      }
    }
    return ShelvedChanges(changeLists, changes, files)
  }

  fun delete(selectedLists: List<DurableRef<ShelvedChangeListEntity>>, selectedChanges: List<ChangeListRpc>) {
    cs.launch {
      val nodes = selectedChanges.flatMap {
        shelfTreeHolder.findChangesInTree(it)
      }
      val changes = findChanges(nodes)
      val changelists = selectedLists.map { shelfTreeHolder.findChangeListNode(it) }.filterIsInstance<ShelvedListNode>().map { it.changeList }
      cs.launch(Dispatchers.EDT) {
        ShelvedChangesViewManager.deleteShelves(project, changelists, changes.changeLists, changes.changes, changes.files)
      }
    }
  }

  fun showStandaloneDiff(dtos: List<ChangeListRpc>, withLocal: Boolean) {
    cs.launch(Dispatchers.EDT) {
      val shelvedChanges = dtos.flatMap { shelfTreeHolder.findChangesInTree(it) }.map { it.shelvedChange }
      val wrappers: ListSelection<ShelvedWrapper> = ListSelection.createAt(shelvedChanges, 0)
      if (wrappers.list.size == 1 && shelvedChanges.size > 1) {
        DiffShelvedChangesActionProvider.showShelvedChangesDiff(project, withLocal, ListSelection.create(shelvedChanges, wrappers.list.first()))
        return@launch
      }
      DiffShelvedChangesActionProvider.showShelvedChangesDiff(project, withLocal, wrappers.asExplicitSelection())
    }
  }

  suspend fun exportPatches() {
    val changeLists = withContext(Dispatchers.EDT) {
      ImportIntoShelfAction.importPatchesToShelf(project)
    }
    if (changeLists.isEmpty()) return

    shelfTreeHolder.scheduleTreeUpdate {
      shelfTreeHolder.selectChangeListInTree(changeLists.first())
    }
  }

  fun navigateToSource(dtos: List<ChangeListRpc>, focusEditor: Boolean) {
    val navigatables = dtos.flatMap { shelfTreeHolder.findChangesInTree(it) }
      .map { it.shelvedChange }
      .map { wrapper -> ShelvedChangeNavigatable(wrapper, project) }
      .toTypedArray()
    cs.launch(Dispatchers.EDT) {
      OpenSourceUtil.navigate(focusEditor, *navigatables)
    }
  }

  fun restoreShelves(selectedLists: List<DurableRef<ShelvedChangeListEntity>>) {
    val currentDate = Date(System.currentTimeMillis())
    selectedLists.mapNotNull { shelfTreeHolder.findChangeListNode(it) as? ShelvedListNode }.forEach {
      shelveChangesManager.restoreList(it.changeList, currentDate)
    }
  }

  suspend fun createPreviewDiffSplitter() {
    shelfTreeHolder.createPreviewDiffSplitter()
  }

  companion object {
    fun getInstance(project: Project): ShelfRemoteActionExecutor = project.service<ShelfRemoteActionExecutor>()
  }

  private class ShelvedChanges(val changeLists: List<ShelvedChangeList>, val changes: List<ShelvedChange>, val files: List<ShelvedBinaryFile>)

  private class ShelvedChangeNavigatable(private val shelvedChange: ShelvedWrapper, private val project: Project) : NavigatableAdapter() {
    override fun navigate(requestFocus: Boolean) {
      val file = shelvedChange.getBeforeVFUnderProject(project) ?: return
      navigate(project, file, true)
    }
  }
}