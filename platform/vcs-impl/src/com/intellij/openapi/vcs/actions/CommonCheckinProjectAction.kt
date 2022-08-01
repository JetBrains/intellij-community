// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_POPUP
import com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_TOOLBAR
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
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

@Deprecated("Use [com.intellij.openapi.vcs.actions.commit.CheckinActionUtil] instead")
open class CommonCheckinProjectAction : AbstractCommonCheckinAction() {
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

  override fun getRoots(dataContext: VcsContext): Array<FilePath> =
    ProjectLevelVcsManager.getInstance(dataContext.project!!).allVcsRoots
      .filter { it.vcs!!.checkinEnvironment != null }
      .map { getFilePath(it.path) }
      .toTypedArray()

  override fun approximatelyHasRoots(dataContext: VcsContext): Boolean = true
}
