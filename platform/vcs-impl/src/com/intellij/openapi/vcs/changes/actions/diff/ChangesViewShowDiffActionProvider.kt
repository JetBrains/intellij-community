// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.DiffRequestProducerException
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager.Companion.getInstance
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.JBIterable
import com.intellij.vcsUtil.VcsImplUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

@ApiStatus.Internal
class ChangesViewShowDiffActionProvider : AnActionExtensionProvider {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isActive(e: AnActionEvent): Boolean {
    return e.getData(ChangesListView.DATA_KEY) != null
  }

  override fun update(e: AnActionEvent) {
    updateAvailability(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val view = e.getData(ChangesListView.DATA_KEY) ?: return
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return

    val changes = view.selectedChanges.toList()
    val unversioned = view.selectedUnversionedFiles.toList()

    val needsConversion = checkIfThereAreFakeRevisions(project, changes)

    val chain = if (needsConversion) {
      val resultRef = CompletableFuture<ListSelection<ChangeDiffRequestChain.Producer?>>()
      // this trick is essential since we are under some conditions to refresh changes;
      // but we can only rely on callback after refresh
      ChangeListManager.getInstance(project).invokeAfterUpdate(true, Runnable {
        ChangesViewManager.getInstanceEx(project).scheduleRefresh(Runnable {
          try {
            val actualChanges = loadFakeRevisions(project, changes)
            resultRef.complete(collectRequestProducers(project, actualChanges, unversioned, view))
          }
          catch (err: Throwable) {
            resultRef.completeExceptionally(err)
          }
        })
      })

      object : ChangeDiffRequestChain.Async() {
        @Throws(DiffRequestProducerException::class)
        override fun loadRequestProducers(): ListSelection<out ChangeDiffRequestChain.Producer?> {
          try {
            return resultRef.get()
          }
          catch (ex: InterruptedException) {
            throw DiffRequestProducerException(ex)
          }
          catch (ex: ExecutionException) {
            throw DiffRequestProducerException(ex)
          }
        }
      }
    }
    else {
      val producers = collectRequestProducers(project, changes, unversioned, view)
      if (producers.isEmpty) return
      ChangeDiffRequestChain(producers)
    }

    chain.putUserData(DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL, true)
    setAllowExcludeFromCommit(project, chain)
    DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.DEFAULT)
  }

  companion object {
    fun updateAvailability(e: AnActionEvent) {
      val project = e.getData(CommonDataKeys.PROJECT)
      val presentation = e.presentation
      val place = e.place

      if (e.getData(ChangesListView.DATA_KEY) == null) {
        presentation.setEnabled(false)
        return
      }

      val changes = JBIterable.of(*e.getData(VcsDataKeys.CHANGES))
      val unversionedFiles = JBIterable.from(e.getData(ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY))

      if (ActionPlaces.MAIN_MENU == place) {
        presentation.setEnabled(project != null && (changes.isNotEmpty || unversionedFiles.isNotEmpty))
      }
      else {
        presentation.setEnabled(project != null && canShowDiff(project, changes, unversionedFiles))
      }

      if (ActionPlaces.CHANGES_VIEW_TOOLBAR == place) {
        presentation.setVisible(!isNewUI())
      }
    }

    private fun canShowDiff(project: Project?, changes: JBIterable<Change>, paths: JBIterable<FilePath>): Boolean {
      return paths.isNotEmpty || changes.filter({ ChangeDiffRequestProducer.canCreate(project, it) }).isNotEmpty
    }

    private fun checkIfThereAreFakeRevisions(project: Project, changes: List<Change>): Boolean {
      var needsConversion = false
      for (change in changes) {
        val beforeRevision = change.beforeRevision
        val afterRevision = change.afterRevision
        if (beforeRevision is FakeRevision) {
          VcsDirtyScopeManager.getInstance(project).fileDirty(beforeRevision.file)
          needsConversion = true
        }
        if (afterRevision is FakeRevision) {
          VcsDirtyScopeManager.getInstance(project).fileDirty(afterRevision.file)
          needsConversion = true
        }
      }
      return needsConversion
    }

    private fun loadFakeRevisions(project: Project, changes: List<Change>): List<Change> {
      val allChanges = ChangeListManager.getInstance(project).getAllChanges()
      return VcsImplUtil.filterChangesUnder(allChanges, ChangesUtil.getPaths(changes)).toList()
    }

    private fun collectRequestProducers(
      project: Project,
      changes: List<Change>,
      unversioned: List<FilePath>,
      changesView: ChangesListView,
    ): ListSelection<ChangeDiffRequestChain.Producer?> {
      if (changes.size == 1 && unversioned.isEmpty()) { // show all changes from this changelist
        val selectedChange: Change = changes.first()
        var selectedChanges = changesView.getAllChangesFromSameChangelist(selectedChange)
        if (selectedChanges == null) {
          selectedChanges = changesView.getAllChangesFromSameAmendNode(selectedChange)
        }
        if (selectedChanges != null) {
          var selectedIndex = selectedChanges.indexOfFirst { ChangeListChange.HASHING_STRATEGY.equals(selectedChange, it) }
          if (selectedIndex == -1) selectedIndex = selectedChanges.indexOf(selectedChange)
          return createChangeProducers(project, selectedChanges, selectedIndex)
        }
      }

      if (unversioned.size == 1 && changes.isEmpty()) { // show all unversioned changes
        val selectedFile = unversioned.first()
        val allUnversioned = changesView.unversionedFiles.toList()
        val selectedIndex = allUnversioned.indexOf(selectedFile)
        return createUnversionedProducers(project, allUnversioned, selectedIndex)
      }

      val changeProducers = createChangeProducers(project, changes, 0)
      val unversionedProducers = createUnversionedProducers(project, unversioned, 0)
      return ListSelection.createAt(
        ContainerUtil.concat(changeProducers.getList(), unversionedProducers.getList()),
        0
      ).asExplicitSelection()
    }

    private fun createChangeProducers(
      project: Project,
      changes: List<Change>,
      selected: Int,
    ): ListSelection<ChangeDiffRequestChain.Producer?> {
      return ListSelection.createAt(changes, selected).map {
        ChangeDiffRequestProducer.create(project, it)
      }
    }

    private fun createUnversionedProducers(
      project: Project,
      unversioned: List<FilePath>,
      selected: Int,
    ): ListSelection<ChangeDiffRequestChain.Producer?> {
      return ListSelection.createAt(unversioned, selected).map {
        UnversionedDiffRequestProducer.create(project, it)
      }
    }

    private fun setAllowExcludeFromCommit(project: Project, chain: DiffRequestChain) {
      val allowExcludeFromCommit = getInstance(project).allowExcludeFromCommit.value
      chain.putUserData(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, allowExcludeFromCommit)
    }
  }
}
