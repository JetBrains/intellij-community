// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.cleanup

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButtonUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.GotItTooltip
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepositoryManager

/**
 * Opens the Branches Cleanup dialog.
 */
internal class CleanupBranchesAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val hasEnoughBranches = project?.let { hasEnoughLocalBranches(it) } ?: false
    e.presentation.isEnabledAndVisible = project != null && hasEnoughBranches

    // Try to show a one-time Got It tooltip when this action is present on the Git Log branches toolbar.
    // We identify the context via VcsLog data keys and the toolbar place used in Branches UI.
    if (project != null && hasEnoughBranches) {
      maybeShowGotItTooltip(e)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    CleanupBranchesDialog(project).show()
  }

  private fun hasEnoughLocalBranches(project: Project): Boolean {
    val repos = GitRepositoryManager.getInstance(project).repositories
    val total = repos.sumOf { it.branches.localBranches.size }
    return total > 4 // according to FUS
  }

  private fun maybeShowGotItTooltip(e: AnActionEvent) { // Only in the VCS Log branches toolbar context
    val inVcsLog = e.getData(VcsLogInternalDataKeys.LOG_DATA) != null
                   || e.getData(VcsLogInternalDataKeys.LOG_UI_EX) != null
                   || e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES) != null
                   || e.getData(VcsLogInternalDataKeys.MAIN_UI) != null
    if (!inVcsLog) return
    if (e.place != "Git.Log.Branches") return

    val contextComponent = e.getData(VcsLogInternalDataKeys.LOG_UI_EX)?.mainComponent ?: return

    val button = ActionButtonUtil.findActionButtonById(contextComponent, "Git.Cleanup.Branches") ?: return

    // Avoid repeated installation for the same button instance
    val shownKey = "git.cleanup.branches.gotit.shown"
    if (button.getClientProperty(shownKey) == true) return

    // Guard against showing too early before the button is laid out
    if (!button.isShowing || button.width == 0 || button.height == 0) return

    GotItTooltip(
      "git.cleanup.branches.gotit",
      GitBundle.message("git.cleanup.branches.gotit.tooltip"),
      e.project
    )
      .withShowCount(3)
      .show(button, GotItTooltip.BOTTOM_MIDDLE)

    button.putClientProperty(shownKey, true)
  }
}
