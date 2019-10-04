// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_POPUP
import com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_TOOLBAR
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.vcsUtil.VcsUtil.getFilePath

private val LOCAL_CHANGES_ACTION_PLACES = setOf(CHANGES_VIEW_TOOLBAR, CHANGES_VIEW_POPUP)

private fun isInLocalChanges(context: VcsContext) = context.place in LOCAL_CHANGES_ACTION_PLACES

private fun isNonModalCommit(context: VcsContext) = context.project?.getNonModalCommitWorkflowHandler() != null

open class CommonCheckinProjectAction : AbstractCommonCheckinAction() {
  private fun isCommitProjectAction() = this::class.java == CommonCheckinProjectAction::class.java

  override fun update(vcsContext: VcsContext, presentation: Presentation) {
    if (isCommitProjectAction() && isInLocalChanges(vcsContext) && isNonModalCommit(vcsContext)) {
      presentation.isEnabledAndVisible = false
    }
    else {
      super.update(vcsContext, presentation)
    }
  }

  override fun getRoots(dataContext: VcsContext): Array<FilePath> =
    ProjectLevelVcsManager.getInstance(dataContext.project!!).allVcsRoots
      .filter { it.vcs!!.checkinEnvironment != null }
      .map { getFilePath(it.path) }
      .toTypedArray()

  override fun approximatelyHasRoots(dataContext: VcsContext): Boolean = true
}
