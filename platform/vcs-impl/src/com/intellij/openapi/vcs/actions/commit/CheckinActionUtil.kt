// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions.commit

import com.intellij.configurationStore.saveSettings
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.actions.DescindingFilesFilter
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.commit.CommitModeManager
import com.intellij.vcs.commit.CommitWorkflowHandler
import com.intellij.vcs.commit.cleanActionText
import com.intellij.vcsUtil.VcsImplUtil

internal fun AnActionEvent.getContextCommitWorkflowHandler(): CommitWorkflowHandler? = getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER)

internal object CheckinActionUtil {
  private val LOG = logger<CheckinActionUtil>()

  fun updateCommonCommitAction(e: AnActionEvent) {
    val project = e.project
    val presentation = e.presentation

    if (project == null ||
        !ProjectLevelVcsManager.getInstance(project).hasActiveVcss() ||
        CommitModeManager.getInstance(project).getCurrentCommitMode().disableDefaultCommitAction()) {
      presentation.isEnabledAndVisible = false
      return
    }

    val projectLevelVcsManager = ProjectLevelVcsManager.getInstance(project)
    val singleVcs = projectLevelVcsManager.singleVCS
    presentation.isEnabled = !projectLevelVcsManager.isBackgroundVcsOperationRunning &&
                             (singleVcs == null || !singleVcs.isCommitActionDisabled)
    presentation.isVisible = true
  }

  fun performCommonCommitAction(e: AnActionEvent,
                                project: Project,
                                initialChangeList: LocalChangeList,
                                pathsToCommit: List<FilePath>,
                                actionName: @NlsActions.ActionText String?,
                                executor: CommitExecutor?,
                                forceUpdateCommitStateFromContext: Boolean) {
    LOG.debug("performCommonCommitAction")

    val isFreezedDialogTitle = actionName?.let {
      val operationName = cleanActionText(actionName)
      VcsBundle.message("error.cant.perform.operation.now", operationName)
    }
    val changeListManager = ChangeListManager.getInstance(project)
    if (changeListManager.isFreezedWithNotification(isFreezedDialogTitle)) {
      LOG.debug("ChangeListManager is freezed, abort commit")
      return
    }

    if (ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning) {
      LOG.debug("Background operation is running, abort commit")
      return
    }

    val selectedChanges = e.getData(VcsDataKeys.CHANGES)?.asList().orEmpty()
    val selectedUnversioned = e.getData(ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY)?.toList().orEmpty()
    val filteredPaths = DescindingFilesFilter.filterDescindingFiles(pathsToCommit.toTypedArray(), project).asList()

    performCheckInAfterUpdate(project, selectedChanges, selectedUnversioned, initialChangeList, filteredPaths,
                              executor, forceUpdateCommitStateFromContext)
  }

  @RequiresEdt
  fun performCheckInAfterUpdate(project: Project,
                                selectedChanges: List<Change>,
                                selectedUnversioned: List<FilePath>,
                                initialChangeList: LocalChangeList,
                                pathsToCommit: List<FilePath>,
                                executor: CommitExecutor?,
                                forceUpdateCommitStateFromContext: Boolean) {
    FileDocumentManager.getInstance().saveAllDocuments()
    runWithModalProgressBlocking(project, IdeBundle.message("progress.saving.project", project.name)) {
      saveSettings(project)
    }

    val workflowHandler = ChangesViewWorkflowManager.getInstance(project).commitWorkflowHandler
    if (executor == null && workflowHandler != null) {
      val included = getIncludedChanges(project, selectedChanges, selectedUnversioned, initialChangeList, pathsToCommit)
      workflowHandler.setCommitState(initialChangeList, included, forceUpdateCommitStateFromContext)
      workflowHandler.activate()
    }
    else {
      ChangeListManager.getInstance(project).invokeAfterUpdateWithModal(
        true, VcsBundle.message("waiting.changelists.update.for.show.commit.dialog.message")) {
        val included = getIncludedChanges(project, selectedChanges, selectedUnversioned, initialChangeList, pathsToCommit)
        if (executor != null) {
          CommitChangeListDialog.commitWithExecutor(project, included, initialChangeList, executor, null, null)
        }
        else {
          CommitChangeListDialog.commitVcsChanges(project, included, initialChangeList, null, null)
        }
      }
    }
  }

  private fun getIncludedChanges(project: Project,
                                 selectedChanges: List<Change>,
                                 selectedUnversioned: List<FilePath>,
                                 initialChangeList: LocalChangeList,
                                 pathsToCommit: List<FilePath>): Collection<Any> {
    if (selectedChanges.isEmpty() && selectedUnversioned.isEmpty()) {
      val allChanges = ChangeListManager.getInstance(project).allChanges
      val changesToCommit = VcsImplUtil.filterChangesUnder(allChanges, pathsToCommit).toSet()
      return initialChangeList.changes.intersect(changesToCommit)
    }
    else {
      return ContainerUtil.concat(selectedChanges, selectedUnversioned)
    }
  }

  fun getInitiallySelectedChangeList(project: Project, e: AnActionEvent): LocalChangeList {
    val manager = ChangeListManager.getInstance(project)

    return e.getData(VcsDataKeys.CHANGE_LISTS)?.firstOrNull()?.let { manager.findChangeList(it.name) }
           ?: e.getData(VcsDataKeys.CHANGES)?.firstOrNull()?.let { manager.getChangeList(it) }
           ?: manager.defaultChangeList
  }

  fun getInitiallySelectedChangeListFor(project: Project, pathsToCommit: List<FilePath>): LocalChangeList {
    val manager = ChangeListManagerEx.getInstanceEx(project)

    val defaultChangeList = manager.defaultChangeList
    val selectedInDefault = VcsImplUtil.filterChangesUnder(defaultChangeList.changes, pathsToCommit)
    if (selectedInDefault.isNotEmpty) {
      return defaultChangeList
    }

    val selectedChanges = VcsImplUtil.filterChangesUnder(manager.allChanges, pathsToCommit)
    val changeLists = manager.getAffectedLists(selectedChanges.toList())
    return changeLists.singleOrNull() ?: defaultChangeList
  }
}
