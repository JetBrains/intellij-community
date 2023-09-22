// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions

import com.intellij.dvcs.branch.DvcsSyncSettings.Value.SYNC
import com.intellij.dvcs.getCommonCurrentBranch
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.ui.table.isEmpty
import com.intellij.vcs.log.ui.table.size
import git4idea.GitRemoteBranch
import git4idea.GitUtil.HEAD
import git4idea.GitUtil.getRepositoryManager
import git4idea.branch.GitBranchUtil
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.createOrCheckoutNewBranch
import org.jetbrains.annotations.Nls

internal class GitCreateNewBranchAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    when (val data = collectData(e)) {
      is Data.WithCommit -> createOrCheckoutNewBranch(data.repository.project, listOf(data.repository), data.hash.toString(),
                                                      GitBundle.message("action.Git.New.Branch.dialog.title", data.hash.toShortString()),
                                                      data.name)
      is Data.NoCommit -> createOrCheckoutNewBranch(data.project, data.repositories, HEAD,
                                                    initialName = data.repositories.getCommonCurrentBranch())
      is Data.Disabled, Data.Invisible -> {}
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    when (val data = collectData(e)) {
      is Data.Invisible -> e.presentation.isEnabledAndVisible = false
      is Data.Disabled -> {
        e.presentation.isVisible = true
        e.presentation.isEnabled = false
        e.presentation.description = data.description
      }
      else -> e.presentation.isEnabledAndVisible = true
    }
  }

  private sealed class Data {
    object Invisible : Data()
    class Disabled(val description: @Nls String) : Data()
    class WithCommit(val repository: GitRepository, val hash: Hash, val name: String?) : Data()
    class NoCommit(val project: Project, val repositories: List<GitRepository>) : Data()
  }

  private fun collectData(e: AnActionEvent): Data {
    val project = e.project ?: return Data.Invisible
    val manager = getRepositoryManager(project)
    if (manager.repositories.isEmpty()) return Data.Invisible

    val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
    if (selection != null) {
      if (selection.isEmpty()) return Data.Invisible
      if (selection.size > 1) return Data.Disabled(GitBundle.message("action.New.Branch.disabled.several.commits.description"))
      val commit = selection.commits.first()
      val repository = manager.getRepositoryForRootQuick(commit.root)
      if (repository != null) {
        val initialName = suggestBranchName(repository, e.getData(VcsLogDataKeys.VCS_LOG_BRANCHES))
        return Data.WithCommit(repository, commit.hash, initialName)
      }
    }

    val repositories =
      if (manager.moreThanOneRoot()) {
        if (GitVcsSettings.getInstance(project).syncSetting == SYNC) manager.repositories
        else {
          val repository = GitBranchUtil.guessRepositoryForOperation(project, e.dataContext)
          repository?.let { listOf(repository) }
        }
      }
      else listOf(manager.repositories.first())

    if (repositories == null || repositories.any { it.isFresh }) {
      return Data.Disabled(GitBundle.message("action.New.Branch.disabled.fresh.description"))
    }
    return Data.NoCommit(project, repositories)
  }

  private fun suggestBranchName(repository: GitRepository, branches: List<VcsRef>?): String? {
    val existingBranches = branches.orEmpty().filter { it.type.isBranch }
    val suggestedNames = existingBranches.map {
      val branch = repository.branches.findBranchByName(it.name) ?: return@map null
      if (branch.isRemote) {
        return@map (branch as GitRemoteBranch).nameForRemoteOperations
      }
      else {
        return@map branch.name
      }
    }
    return suggestedNames.distinct().singleOrNull()
  }
}
