// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.*
import git4idea.branch.GitBrancher
import git4idea.i18n.GitBundle
import git4idea.log.GitRefManager.LOCAL_BRANCH
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls
import java.util.*

internal class GitCheckoutActionGroup : GitSingleCommitActionGroup(GitBundle.message("git.log.action.checkout.group"), false) {

  override fun getChildren(e: AnActionEvent, project: Project, log: VcsLog, repository: GitRepository, commit: CommitId): Array<AnAction> {
    val refNames = getRefNames(e, log, repository)
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
    val checkoutRevision = ActionManager.getInstance().getAction("Git.CheckoutRevision")
    return EmptyAction.wrap(checkoutRevision).also { it.templatePresentation.text = checkoutRevisionText }
  }

  private fun getRefNames(e: AnActionEvent, log: VcsLog, repository: GitRepository): List<String> {
    val refs = e.getData(VcsLogDataKeys.VCS_LOG_REFS) ?: emptyList()
    val localBranches = refs.filterTo(mutableListOf()) { it.type == LOCAL_BRANCH }
    val provider = log.logProviders[repository.root]
    if (provider != null) {
      ContainerUtil.sort<VcsRef>(localBranches, provider.referenceManager.labelsOrderComparator)
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
                                actionText: @Nls String) : DumbAwareAction() {

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