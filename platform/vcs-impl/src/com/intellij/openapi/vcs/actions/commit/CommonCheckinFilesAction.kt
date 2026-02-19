// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions.commit

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.actions.VcsContextUtil
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class CommonCheckinFilesAction : DumbAwareAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    CheckinActionUtil.updateCommonCommitAction(e)

    val project = e.project
    val presentation = e.presentation

    if (project != null) {
      val pathsToCommit = VcsContextUtil.selectedFilePaths(e.dataContext)
      presentation.text = Manager.getActionName(project, pathsToCommit) + StringUtil.ELLIPSIS

      presentation.isEnabled = presentation.isEnabled && pathsToCommit.any { Manager.isActionEnabled(project, it) }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val pathsToCommit = VcsContextUtil.selectedFilePaths(e.dataContext)
    val initialChangeList = CheckinActionUtil.getInitiallySelectedChangeListFor(project, pathsToCommit)
    val actionName = Manager.getActionName(project, pathsToCommit)

    CheckinActionUtil.performCommonCommitAction(e, project, initialChangeList, pathsToCommit, actionName, null, true)
  }

  object Manager {
    @ApiStatus.Internal
    fun getActionName(project: Project, pathsToCommit: List<FilePath>): @NlsActions.ActionText String {
      val commonVcs = pathsToCommit.mapNotNull { VcsUtil.getVcsFor(project, it) }.distinct().singleOrNull()
      val operationName = commonVcs?.checkinEnvironment?.checkinOperationName
      return appendSubject(pathsToCommit, operationName ?: VcsBundle.message("vcs.command.name.checkin"))
    }

    private fun appendSubject(roots: List<FilePath>, checkinActionName: @Nls String): @NlsActions.ActionText String {
      if (roots.isEmpty()) return checkinActionName

      if (roots[0].isDirectory) {
        return VcsBundle.message("action.name.checkin.directory", checkinActionName, roots.size)
      }
      else {
        return VcsBundle.message("action.name.checkin.file", checkinActionName, roots.size)
      }
    }

    @ApiStatus.Internal
    fun isActionEnabled(project: Project, path: FilePath): Boolean {
      val status = ChangeListManager.getInstance(project).getStatus(path)
      return (path.isDirectory || status != FileStatus.NOT_CHANGED) && status != FileStatus.IGNORED
    }
  }
}
