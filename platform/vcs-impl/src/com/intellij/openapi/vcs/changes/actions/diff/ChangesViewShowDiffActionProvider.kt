// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalChangeListDiffTool
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.ChangesListViewDiffableSelectionUtil
import com.intellij.openapi.vcs.changes.ui.DiffSource
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.util.containers.JBIterable
import org.jetbrains.annotations.ApiStatus

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

    val selection = ChangesListViewDiffableSelectionUtil.computeSelectionForDiff(view)
    val producers = selection.mapToProducers(project)
    if (producers.isEmpty) return
    val chain = ChangeDiffRequestChain(producers).apply {
      putUserData(DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL, true)
      val allowExcludeFromCommit = ChangesViewWorkflowManager.getInstance(project).allowExcludeFromCommit.value
      putUserData(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, allowExcludeFromCommit)
    }

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
  }
}

private fun ListSelection<DiffSource>.mapToProducers(project: Project): ListSelection<out ChangeDiffRequestChain.Producer?> {
  return map {
    when (it) {
      is DiffSource.Change -> ChangeDiffRequestProducer.create(project, it.change)
      is DiffSource.UnversionedFile -> UnversionedDiffRequestProducer.create(project, it.filePath)
    }
  }
}
