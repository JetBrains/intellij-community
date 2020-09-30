// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.dvcs.DvcsUtil.guessVcsRoot
import com.intellij.dvcs.branch.DvcsSyncSettings.Value.SYNC
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.VcsRef
import git4idea.GitRemoteBranch
import git4idea.GitUtil.HEAD
import git4idea.GitUtil.getRepositoryManager
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.createOrCheckoutNewBranch

internal class GitCreateNewBranchAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    when (val data = collectData(e)) {
      is Data.WithCommit -> createOrCheckoutNewBranch(data.repository.project, listOf(data.repository), data.hash.toString(),
                                                      GitBundle.message("action.Git.New.Branch.dialog.title", data.hash.toShortString()),
                                                      data.name)
      is Data.NoCommit -> createOrCheckoutNewBranch(data.project, data.repositories, HEAD)
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
    class Disabled(val description : String) : Data()
    class WithCommit(val repository: GitRepository, val hash: Hash, val name: String?) : Data()
    class NoCommit(val project: Project, val repositories: List<GitRepository>) : Data()
  }

  private fun collectData(e: AnActionEvent): Data {
    val project = e.project ?: return Data.Invisible
    val manager = getRepositoryManager(project)
    if (manager.repositories.isEmpty()) return Data.Invisible

    val log = e.getData(VcsLogDataKeys.VCS_LOG)
    if (log != null) {
      val commits = log.selectedCommits
      if (commits.isEmpty()) return Data.Invisible
      if (commits.size > 1) return Data.Disabled(GitBundle.message("action.New.Branch.disabled.several.commits.description"))
      val commit = commits.first()
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
          val repository = manager.getRepositoryForRootQuick(guessVcsRoot(project, e.getData(VIRTUAL_FILE)))
          repository?.let { listOf(repository) }
        }
      }
      else listOf(manager.repositories.first())

    if (repositories == null || repositories.any { it.isFresh }) return Data.Disabled(GitBundle.message("action.New.Branch.disabled.fresh.description"))
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
