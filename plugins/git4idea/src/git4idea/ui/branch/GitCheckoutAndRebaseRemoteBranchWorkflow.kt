// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch

import com.intellij.ide.IdeBundle
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.GitNotificationIdsHolder
import git4idea.branch.GitBranchUiHandlerImpl
import git4idea.branch.GitBrancher
import git4idea.branch.GitNewBranchOptions
import git4idea.commands.Git
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository

internal class GitCheckoutAndRebaseRemoteBranchWorkflow(private val project: Project, private val repositories: List<GitRepository>) {

  private val brancher = GitBrancher.getInstance(project)

  init {
    assert(repositories.isNotEmpty())
  }

  fun execute(startPoint: String, options: GitNewBranchOptions) {
    val name = options.name
    val reset = options.reset

    if (repositories.any { it.currentBranchName == name }) {
      VcsNotifier.getInstance(project)
        .notifyError(
          GitNotificationIdsHolder.BRANCH_OPERATION_ERROR,
          GitBundle.message("branches.checkout.and.rebase.failed"),
          GitBundle.message("branches.checkout.and.rebase.error.current.with.same.name", name),
        )
      return
    }

    val localHasMoreCommits = GitBranchCheckoutOperation.checkLocalHasMoreCommits(project, repositories, name, startPoint)
    if (localHasMoreCommits) {
      if (reset) {
        val result = Messages.showYesNoDialog(
          GitBundle.message("branches.create.with.reset.local.has.more.commits", name, startPoint),
          GitBundle.message("checkout.0", startPoint),
          GitBundle.message("branches.drop.local.commits"), IdeBundle.message("button.cancel"), null)
        if (result != Messages.YES) return
      }
      else {
        val result = Messages.showYesNoDialog(
          GitBundle.message("branches.create.local.has.more.commits", name, startPoint),
          GitBundle.message("checkout.0", startPoint),
          GitBundle.message("new.branch.dialog.operation.create.name"), IdeBundle.message("button.cancel"), null)
        if (result != Messages.YES) return
      }
    }

    object : Task.Backgroundable(project, GitBundle.message("branches.checkout.and.rebase.onto.current.process", startPoint), true) {
      override fun run(indicator: ProgressIndicator) {
        try {
          indicator.text2 = GitBundle.message("branch.creating.branch.process", name)
          doCreateNewBranch(startPoint, name, reset)
        }
        catch (e: Exception) {
          GitBranchUiHandlerImpl(project, indicator)
            .notifyError(GitBundle.message("create.branch.operation.could.not.create.new.branch", name), e.message!!)
        }
        indicator.text2 = GitBundle.message("branch.rebasing.process", name)
        brancher.rebaseOnCurrent(repositories, name)
      }
    }.queue()
  }

  private fun doCreateNewBranch(startPoint: String, name: String, reset: Boolean) {
    val (reposWithLocalBranch, reposWithoutLocalBranch) = repositories.partition { it.branches.findLocalBranch(name) != null }

    if (reposWithLocalBranch.isNotEmpty() && reset) {
      createBranch(name, startPoint, reposWithLocalBranch, true)
    }

    if (reposWithoutLocalBranch.isNotEmpty()) {
      createBranch(name, startPoint, reposWithoutLocalBranch, false)
    }
  }

  private fun createBranch(name: String,
                           startPoint: String,
                           repositories: List<GitRepository>,
                           force: Boolean) {
    val git = Git.getInstance()
    for (repository in repositories) {
      val result = git.branchCreate(repository, name, startPoint, force)
      if (result.success()) {
        repository.update()
      }
      else {
        throw IllegalStateException(result.errorOutputAsHtmlString)
      }
    }
  }
}