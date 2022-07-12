// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_POPUP
import com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_TOOLBAR
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.vcs.commit.CommitMode
import com.intellij.vcs.commit.getProjectCommitMode
import com.intellij.vcsUtil.VcsUtil.getFilePath

private val LOCAL_CHANGES_ACTION_PLACES = setOf(CHANGES_VIEW_TOOLBAR, CHANGES_VIEW_POPUP)
private fun AnActionEvent.isFromLocalChangesPlace() = place in LOCAL_CHANGES_ACTION_PLACES
private fun AnActionEvent.isFromLocalChanges() = getData(ChangesViewContentManager.CONTENT_TAB_NAME_KEY) == LOCAL_CHANGES

private fun AnActionEvent.isToggleCommitEnabled(): Boolean {
  val commitMode = getProjectCommitMode()
  return commitMode is CommitMode.NonModalCommitMode &&
         commitMode.isToggleMode
}

open class CommonCheckinProjectAction : CommonCheckinProjectActionImpl() {
  override fun update(e: AnActionEvent) {
    if (e.isToggleCommitEnabled() && e.isFromLocalChanges()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val commitMode = e.getProjectCommitMode()
    if (commitMode is CommitMode.NonModalCommitMode && !commitMode.isToggleMode && e.isFromLocalChangesPlace()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    super.update(e)
  }
}

class ToggleChangesViewCommitUiAction : DumbAwareToggleAction() {
  private val commitProjectAction = CommonCheckinProjectActionImpl()

  init {
    ActionUtil.copyFrom(this, IdeActions.ACTION_CHECKIN_PROJECT)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    if (!(e.isToggleCommitEnabled() && e.isFromLocalChanges())) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    if (isSelected(e)) {
      e.presentation.isEnabledAndVisible = true
      return
    }

    commitProjectAction.update(e)
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
      commitProjectAction.actionPerformed(e)
    }
  }
}

open class CommonCheckinProjectActionImpl : AbstractCommonCheckinAction() {
  override fun getRoots(dataContext: VcsContext): Array<FilePath> =
    ProjectLevelVcsManager.getInstance(dataContext.project!!).allVcsRoots
      .filter { it.vcs!!.checkinEnvironment != null }
      .map { getFilePath(it.path) }
      .toTypedArray()

  override fun approximatelyHasRoots(dataContext: VcsContext): Boolean = true
}
