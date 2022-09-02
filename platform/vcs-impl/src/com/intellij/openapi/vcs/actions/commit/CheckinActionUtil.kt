// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions.commit

import com.intellij.configurationStore.StoreUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
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
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.commit.CommitModeManager
import com.intellij.vcs.commit.CommitWorkflowHandler
import com.intellij.vcs.commit.cleanActionText

internal fun AnActionEvent.getContextCommitWorkflowHandler(): CommitWorkflowHandler? = getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER)

object CheckinActionUtil {
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

    presentation.isEnabled = !ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning
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
    StoreUtil.saveDocumentsAndProjectSettings(project)

    ChangeListManager.getInstance(project).invokeAfterUpdateWithModal(
      true, VcsBundle.message("waiting.changelists.update.for.show.commit.dialog.message")) {
      performCheckIn(project, selectedChanges, selectedUnversioned, initialChangeList, pathsToCommit,
                     executor, forceUpdateCommitStateFromContext)
    }

  }

  private fun performCheckIn(project: Project,
                             selectedChanges: List<Change>,
                             selectedUnversioned: List<FilePath>,
                             initialChangeList: LocalChangeList,
                             pathsToCommit: List<FilePath>,
                             executor: CommitExecutor?,
                             forceUpdateCommitStateFromContext: Boolean) {
    val changesToCommit: Collection<Change>
    val included: Collection<Any>

    if (selectedChanges.isEmpty() && selectedUnversioned.isEmpty()) {
      val manager = ChangeListManager.getInstance(project)
      changesToCommit = pathsToCommit.flatMap { manager.getChangesIn(it) }.toSet()
      included = initialChangeList.changes.intersect(changesToCommit)
    }
    else {
      changesToCommit = selectedChanges.toList()
      included = ContainerUtil.concat(changesToCommit, selectedUnversioned)
    }

    val workflowHandler = ChangesViewWorkflowManager.getInstance(project).commitWorkflowHandler
    if (executor == null && workflowHandler != null) {
      LOG.debug("invoking commit workflow after update")
      workflowHandler.setCommitState(initialChangeList, included, forceUpdateCommitStateFromContext)
      workflowHandler.activate()
    }
    else {
      LOG.debug("invoking commit dialog after update")
      CommitChangeListDialog.commitChanges(project, changesToCommit, included, initialChangeList, executor, null)
    }
  }

  fun getInitiallySelectedChangeList(project: Project, e: AnActionEvent): LocalChangeList {
    val manager = ChangeListManager.getInstance(project)

    return e.getData(VcsDataKeys.CHANGE_LISTS)?.firstOrNull()?.let { manager.findChangeList(it.name) }
           ?: e.getData(VcsDataKeys.CHANGES)?.firstOrNull()?.let { manager.getChangeList(it) }
           ?: manager.defaultChangeList
  }

  fun getInitiallySelectedChangeListFor(project: Project, pathsToCommit: List<FilePath>): LocalChangeList {
    val manager = ChangeListManager.getInstance(project)

    val defaultChangeList = manager.defaultChangeList
    val defaultListChanges = defaultChangeList.changes

    var result: LocalChangeList? = null
    for (filePath in pathsToCommit) {
      val changes = manager.getChangesIn(filePath)
      if (changes.any { defaultListChanges.contains(it) }) return defaultChangeList

      if (result == null) {
        result = changes.firstNotNullOfOrNull { manager.getChangeList(it) }
      }
    }

    return result ?: defaultChangeList
  }
}
