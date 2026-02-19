// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.update.UpdateOptionsDialog
import git4idea.GitUtil
import git4idea.config.UpdateMethod
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchPopupActions
import java.awt.event.ActionEvent
import javax.swing.Action

internal class GitUpdateOptionsDialog(
  project: Project,
  title: @NlsSafe String,
  envToConfMap: LinkedHashMap<Configurable, AbstractVcs>,
) : UpdateOptionsDialog(project, title, envToConfMap) {
  override fun createLeftSideActions(): Array<Action> {
    val baseLeft = super.createLeftSideActions()
    val repository = GitUtil.getRepositoryManager(myProject).repositories.singleOrNull() ?: return baseLeft

    return arrayOf(ResetToRemoteBranchAction(repository), *baseLeft)
  }

  private fun askForConfirmation(repository: GitRepository): Boolean {
    val localBranch = repository.currentBranch ?: run {
      VcsNotifier.getInstance(myProject).notifyDetachedHeadError(repository)
      return false
    }
    val remoteBranch = localBranch.findTrackedBranch(repository) ?: run {
      VcsNotifier.getInstance(myProject).notifyNoTrackedBranchError(repository, localBranch)
      return false
    }

    val localBranchPresentation = GitBranchPopupActions.getSelectedBranchFullPresentation(localBranch.name)
    val remoteBranchPresentation = GitBranchPopupActions.getSelectedBranchFullPresentation(remoteBranch.name)

    return MessageDialogBuilder.okCancel(GitBundle.message("action.Git.Update.Reset.To.Remote.Branch.text"),
                                         GitBundle.message("action.Git.Update.Reset.To.Remote.Branch.confirmation", localBranchPresentation, remoteBranchPresentation))
      .asWarning()
      .ask(myProject)
  }

  private inner class ResetToRemoteBranchAction(
    private val repository: GitRepository,
  ) : DialogWrapperAction(GitBundle.message("action.Git.Update.Reset.To.Remote.Branch.text")) {
    override fun doAction(e: ActionEvent) {
      doCancelAction()

      if (askForConfirmation(repository)) {
        GitUpdateExecutionProcess.launchUpdate(myProject, listOf(repository), null, UpdateMethod.RESET)
      }
    }
  }
}