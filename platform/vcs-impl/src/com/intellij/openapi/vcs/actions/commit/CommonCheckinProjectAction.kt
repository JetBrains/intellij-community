// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions.commit

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_POPUP
import com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_TOOLBAR
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.vcs.commit.CommitMode
import com.intellij.vcs.commit.getProjectCommitMode
import com.intellij.vcsUtil.VcsUtil.getFilePath
import org.jetbrains.annotations.ApiStatus

private val LOCAL_CHANGES_ACTION_PLACES = setOf(CHANGES_VIEW_TOOLBAR, CHANGES_VIEW_POPUP)
private fun AnActionEvent.isFromLocalChangesPlace() = place in LOCAL_CHANGES_ACTION_PLACES
private fun AnActionEvent.isFromLocalChanges() = getData(ChangesViewContentManager.CONTENT_TAB_NAME_KEY) == LOCAL_CHANGES

private fun AnActionEvent.isToggleCommitEnabled(): Boolean {
  val commitMode = getProjectCommitMode()
  return commitMode is CommitMode.NonModalCommitMode &&
         commitMode.isToggleMode
}

@ApiStatus.Internal
fun AnActionEvent.isCommonCommitActionHidden(): Boolean {
  if (isToggleCommitEnabled() && isFromLocalChanges()) {
    // Show toggle button instead, ToggleChangesViewCommitUiAction
    return true
  }

  val commitMode = getProjectCommitMode()
  // Hide from LocalChanges toolwindow in non-modal commit (we use a commit workflow button instead).
  return commitMode is CommitMode.NonModalCommitMode && !commitMode.isToggleMode && isFromLocalChangesPlace()
}

class CommonCheckinProjectAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    if (e.isCommonCommitActionHidden()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    CheckinActionUtil.updateCommonCommitAction(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    performCheckinProjectAction(e)
  }
}

@ApiStatus.Internal
class ToggleChangesViewCommitUiAction : DumbAwareToggleAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.text = ActionsBundle.message("action.CheckinProject.text")

    if (!(e.isToggleCommitEnabled() && e.isFromLocalChanges())) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    if (isSelected(e)) {
      e.presentation.isEnabledAndVisible = true
      return
    }

    CheckinActionUtil.updateCommonCommitAction(e)
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    val workflowManager = project.getServiceIfCreated(ChangesViewWorkflowManager::class.java) ?: return false
    val workflowHandler = workflowManager.commitWorkflowHandler
    return workflowHandler?.isActive == true
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val workflowHandler = ChangesViewWorkflowManager.getInstance(e.project!!).commitWorkflowHandler
    if (workflowHandler != null && workflowHandler.isActive) {
      workflowHandler.deactivate(false)
    }
    else {
      performCheckinProjectAction(e)
    }
  }
}

private fun performCheckinProjectAction(e: AnActionEvent) {
  val project = e.project!!
  val roots = getAllCommittableRoots(project)

  val initialChangelist = CheckinActionUtil.getInitiallySelectedChangeList(project, e)

  CheckinActionUtil.performCommonCommitAction(e, project, initialChangelist, roots, ActionsBundle.message("action.CheckinProject.text"),
                                              null, false)
}

@ApiStatus.Internal
fun getAllCommittableRoots(project: Project): List<FilePath> {
  return ProjectLevelVcsManager.getInstance(project).allVcsRoots
    .filter { it.vcs?.checkinEnvironment != null }
    .map { getFilePath(it.path) }
}
