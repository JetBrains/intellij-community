// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsLog
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.VcsRef
import git4idea.branch.GitBrancher
import git4idea.log.GitRefManager.LOCAL_BRANCH
import git4idea.repo.GitRepository
import java.util.*

internal class GitCheckoutActionGroup : GitSingleCommitActionGroup("Checkout", false) {

  override fun getChildren(e: AnActionEvent, project: Project, log: VcsLog, repository: GitRepository, commit: CommitId): Array<AnAction> {
    val refNames = getRefNames(e, log, repository)
    val actions = ArrayList<AnAction>()
    for (refName in refNames) {
      actions.add(GitCheckoutAction(project, repository, refName, refName))
    }

    val hasMultipleActions = !actions.isEmpty()
    
    val checkoutRevisionText = "${if (hasMultipleActions) "" else "Checkout "}Revision '${commit.hash.toShortString()}'"
    actions.add(GitCheckoutAction(project, repository, commit.hash.asString(), checkoutRevisionText))

    val mainGroup = DefaultActionGroup("Checkout", actions)
    mainGroup.isPopup = hasMultipleActions
    return arrayOf(mainGroup)
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

private class GitCheckoutAction(private val project: Project,
                                private val repository: GitRepository,
                                private val hashOrRefName: String,
                                actionText: String) : DumbAwareAction() {
  init {
    templatePresentation.setText(actionText, false)
  }

  override fun actionPerformed(e: AnActionEvent) {
    GitBrancher.getInstance(project).checkout(hashOrRefName, false, listOf(repository), null)
  }
}