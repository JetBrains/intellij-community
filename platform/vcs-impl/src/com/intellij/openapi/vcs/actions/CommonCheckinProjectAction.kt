// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_POPUP
import com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_TOOLBAR
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTENT_MANAGER
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.vcsUtil.VcsUtil.getFilePath

internal val isToggleCommitUi = Registry.get("vcs.non.modal.commit.toggle.ui")

private val LOCAL_CHANGES_ACTION_PLACES = setOf(CHANGES_VIEW_TOOLBAR, CHANGES_VIEW_POPUP)
private fun AnActionEvent.isFromLocalChangesPlace() = place in LOCAL_CHANGES_ACTION_PLACES

internal fun AnActionEvent.isFromLocalChanges() = getData(CONTENT_MANAGER)?.selectedContent?.tabName == LOCAL_CHANGES

open class CommonCheckinProjectAction : AbstractCommonCheckinAction() {
  protected open fun isCommitProjectAction() = this::class.java == CommonCheckinProjectAction::class.java

  override fun update(e: AnActionEvent) {
    if (isCommitProjectAction() && e.isProjectUsesNonModalCommit() &&
        (isToggleCommitUi.asBoolean() && e.isFromLocalChanges() || !isToggleCommitUi.asBoolean() && e.isFromLocalChangesPlace())) {
      e.presentation.isEnabledAndVisible = false
    }
    else {
      super.update(e)
    }
  }

  override fun getRoots(dataContext: VcsContext): Array<FilePath> =
    ProjectLevelVcsManager.getInstance(dataContext.project!!).allVcsRoots
      .filter { it.vcs!!.checkinEnvironment != null }
      .map { getFilePath(it.path) }
      .toTypedArray()

  override fun approximatelyHasRoots(dataContext: VcsContext): Boolean = true
}

private class ToggleChangesViewCommitUiAction : DumbAwareToggleAction() {
  // need to use `CommonCheckinProjectAction` inheritor - otherwise action is invisible in Local Changes
  private val commitProjectAction = object : CommonCheckinProjectAction() {}

  init {
    ActionUtil.copyFrom(this, IdeActions.ACTION_CHECKIN_PROJECT)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    when {
      !e.isProjectUsesNonModalCommit() || !isToggleCommitUi.asBoolean() || !e.isFromLocalChanges() ->
        e.presentation.isEnabledAndVisible = false

      isSelected(e) -> e.presentation.isEnabledAndVisible = true
      else -> commitProjectAction.update(e)
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean = e.getProjectCommitWorkflowHandler()?.isActive == true

  override fun setSelected(e: AnActionEvent, state: Boolean) =
    if (state) {
      commitProjectAction.actionPerformed(e)
    }
    else {
      e.getProjectCommitWorkflowHandler()!!.deactivate(false)
    }
}
