// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions

import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.merge.MergeConflictManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import git4idea.i18n.GitBundle
import git4idea.merge.GitMergeUtil

internal class GitRevertResolvedAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val files = e.getData(VcsDataKeys.VIRTUAL_FILES)?.toList()
    val mergeConflictManager = project?.getService(MergeConflictManager::class.java)

    e.presentation.isEnabledAndVisible = project != null &&
                                         !files.isNullOrEmpty() &&
                                         mergeConflictManager != null &&
                                         files.all { mergeConflictManager.isResolvedConflict(VcsUtil.getFilePath(it)) }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val files = e.getData(VcsDataKeys.VIRTUAL_FILES)?.toList() ?: return

    rollbackFiles(project, files)
  }

  companion object {
    @JvmStatic
    fun rollbackFiles(project: Project, files: List<VirtualFile>) {
      val message =
        GitBundle.message("action.Git.RevertResolved.confirmation",
                          files.size, files.joinToString(separator = "\n") { it.presentableUrl })
      val yes = CommonBundle.getYesButtonText()
      val no = CommonBundle.getNoButtonText()
      val result = MessageDialogBuilder.Message(GitBundle.message("action.Git.RevertResolved.confirmation.title"), message)
        .buttons(yes, no)
        .defaultButton(no)
        .focusedButton(no)
        .show(project)
      if (result != yes) return

      object : Task.Backgroundable(project, GitBundle.message("action.Git.RevertResolved.progress"), true) {
        override fun run(indicator: ProgressIndicator) {
          GitMergeUtil.revertMergedFiles(project, files)
          VcsDirtyScopeManager.getInstance(project).filesDirty(files, emptyList())
        }
      }.queue()
    }
  }
}
