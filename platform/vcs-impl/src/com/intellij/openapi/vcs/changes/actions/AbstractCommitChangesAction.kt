// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.actions.AbstractCommonCheckinAction
import com.intellij.openapi.vcs.actions.VcsContext
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.util.ArrayUtil
import com.intellij.util.ObjectUtils.notNull
import java.util.stream.Stream

abstract class AbstractCommitChangesAction : AbstractCommonCheckinAction() {
  override fun getRoots(context: VcsContext): Array<FilePath> = AbstractCommonCheckinAction.getAllContentRoots(context)

  override fun approximatelyHasRoots(dataContext: VcsContext): Boolean = ProjectLevelVcsManager.getInstance(
    dataContext.project!!).hasAnyMappings()

  override fun update(vcsContext: VcsContext, presentation: Presentation) {
    super.update(vcsContext, presentation)

    if (presentation.isEnabledAndVisible) {
      val changes = vcsContext.selectedChanges

      if (vcsContext.place == ActionPlaces.CHANGES_VIEW_POPUP) {
        val changeLists = vcsContext.selectedChangeLists

        presentation.isEnabled = if (!ArrayUtil.isEmpty(changeLists))
          changeLists!!.size == 1 && !changeLists[0].changes.isEmpty()
        else
          !ArrayUtil.isEmpty(changes)
      }

      if (presentation.isEnabled && !ArrayUtil.isEmpty(changes)) {
        disableIfAnyHijackedChanges(notNull(vcsContext.project), presentation, changes!!)
      }
    }
  }

  private fun disableIfAnyHijackedChanges(project: Project, presentation: Presentation, changes: Array<Change>) {
    val manager = ChangeListManager.getInstance(project)
    val hasHijackedChanges = Stream.of(*changes).anyMatch { change ->
      change.fileStatus === FileStatus.HIJACKED && manager.getChangeList(change) == null
    }

    presentation.isEnabled = !hasHijackedChanges
  }
}