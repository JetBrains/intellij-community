// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions.commit

import com.intellij.configurationStore.StoreUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil

object CheckinActionUtil {
  private val LOG = logger<CheckinActionUtil>()

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
}