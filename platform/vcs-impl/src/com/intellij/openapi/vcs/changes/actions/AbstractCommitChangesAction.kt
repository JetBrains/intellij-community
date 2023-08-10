// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.actions.AbstractCommonCheckinAction
import com.intellij.openapi.vcs.actions.VcsContext
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.vcsUtil.VcsUtil.getFilePath

@Deprecated("Use [com.intellij.openapi.vcs.actions.commit.AbstractCommitChangesAction] instead")
abstract class AbstractCommitChangesAction : AbstractCommonCheckinAction() {
  override fun getRoots(dataContext: VcsContext): Array<FilePath> =
    ProjectLevelVcsManager.getInstance(dataContext.project!!).allVersionedRoots.map { getFilePath(it) }.toTypedArray()

  override fun approximatelyHasRoots(dataContext: VcsContext): Boolean =
    ProjectLevelVcsManager.getInstance(dataContext.project!!).hasActiveVcss()

  override fun update(vcsContext: VcsContext, presentation: Presentation) {
    super.update(vcsContext, presentation)

    if (presentation.isEnabledAndVisible) {
      val changes = vcsContext.selectedChanges

      if (vcsContext.place == ActionPlaces.CHANGES_VIEW_POPUP) {
        val changeLists = vcsContext.selectedChangeLists

        presentation.isEnabled =
          if (changeLists.isNullOrEmpty()) !changes.isNullOrEmpty() else changeLists.size == 1 && !changeLists[0].changes.isEmpty()
      }

      if (presentation.isEnabled && !changes.isNullOrEmpty()) {
        val manager = ChangeListManager.getInstance(vcsContext.project!!)
        presentation.isEnabled = changes.all { isActionEnabled(manager, it) }
      }
    }
  }

  protected open fun isActionEnabled(manager: ChangeListManager,
                                     it: Change) = manager.getChangeList(it) != null
}