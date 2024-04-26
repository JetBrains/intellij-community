// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions.ActionText
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.*
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import git4idea.branch.GitBrancher
import git4idea.i18n.GitBundle
import git4idea.log.GitRefManager.Companion.LOCAL_BRANCH
import git4idea.repo.GitRepository

internal class GitCheckoutActionGroup : GitSingleCommitActionGroup(GitBundle.message("git.log.action.checkout.group"), false) {

  override fun getChildren(e: AnActionEvent, project: Project, repository: GitRepository, commit: CommitId): Array<AnAction> {
    val refNames = getRefNames(e, repository)
    val actions = ArrayList<AnAction>()
    for (refName in refNames) {
      actions.add(GitCheckoutAction(project, repository, refName, refName))
    }

    val hasMultipleActions = actions.isNotEmpty()

    actions.add(getCheckoutRevisionAction(commit, hasMultipleActions))

    val mainGroup = DefaultActionGroup(GitBundle.message("git.log.action.checkout.group"), actions)
    mainGroup.isPopup = hasMultipleActions
    return arrayOf(mainGroup)
  }

  private fun getCheckoutRevisionAction(commit: CommitId, useShortText: Boolean): AnAction {
    val hashString = commit.hash.toShortString()
    val checkoutRevisionText = if (useShortText) {
      GitBundle.message("git.log.action.checkout.revision.short.text", hashString)
    }
    else {
      GitBundle.message("git.log.action.checkout.revision.full.text", hashString)
    }
    return ActionUtil.wrap("Git.CheckoutRevision").also { it.templatePresentation.text = checkoutRevisionText }
  }

  private fun getRefNames(e: AnActionEvent, repository: GitRepository): List<String> {
    val refs = e.getData(VcsLogDataKeys.VCS_LOG_REFS) ?: emptyList()
    val localBranches = refs.filterTo(mutableListOf()) { it.type == LOCAL_BRANCH }
    e.getData(VcsLogInternalDataKeys.LOG_DATA)?.logProviders?.get(repository.root)?.let { provider ->
      ContainerUtil.sort(localBranches, provider.referenceManager.labelsOrderComparator)
    }
    val refNames = localBranches.map { it.name }
    val currentBranchName = repository.currentBranchName ?: return refNames
    return refNames.minus(currentBranchName)
  }
}

private fun checkout(project: Project, repository: GitRepository, hashOrRefName: String) {
  GitBrancher.getInstance(project).checkout(hashOrRefName, false, listOf(repository), null)
}

private class GitCheckoutAction(private val project: Project,
                                private val repository: GitRepository,
                                private val hashOrRefName: String,
                                @ActionText actionText: String) : DumbAwareAction() {

  init {
    templatePresentation.setText(actionText, false)
  }

  override fun actionPerformed(e: AnActionEvent) {
    checkout(project, repository, hashOrRefName)
  }
}

class GitCheckoutRevisionAction : GitLogSingleCommitAction() {
  override fun actionPerformed(repository: GitRepository, commit: Hash) = checkout(repository.project, repository, commit.asString())
}