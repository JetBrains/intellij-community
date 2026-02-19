// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import git4idea.branch.GitBrancher
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class GitRebaseOntoCommitAction(
  private val myProject: Project,
  private val repository: GitRepository,
  private val commitId: CommitId,
) : DumbAwareAction() {

  private val root: VirtualFile get() = commitId.root
  private val hash: Hash get() = commitId.hash

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val shortHash = hash.toShortString()
    val p = e.presentation

    val truncatedBranch = GitBranchPopupActions.getCurrentBranchTruncatedPresentation(myProject, listOf(repository))
    val actionText = GitBundle.message("branches.rebase.onto.selected.commit", truncatedBranch)
    p.text = actionText

    val isOnBranch = repository.isOnBranch
    if (!isOnBranch) {
      p.description = GitBundle.message("branches.rebase.is.not.possible.in.the.detached.head.state")
      p.isVisible = true
      p.isEnabled = false
      return
    }

    p.description = GitBundle.message("branches.rebase.onto", truncatedBranch, shortHash)
    p.isEnabledAndVisible = true

    val logData = VcsLogInternalDataKeys.LOG_DATA.getData(e.dataContext) ?: return
    val currentBranchCondition = logData.containingBranchesGetter.getContainedInCurrentBranchCondition(root)
    val commitIndex = logData.getCommitIndex(hash, root)
    val isCommitInCurrentBranch = currentBranchCondition.test(commitIndex)

    val subject = logData.miniDetailsGetter.getCommitData(commitIndex, setOf(commitIndex)).subject
    p.description = GitBundle.message("branches.rebase.onto", truncatedBranch, "$shortHash \"$subject\"")

    p.isEnabledAndVisible = !isCommitInCurrentBranch
  }

  override fun actionPerformed(e: AnActionEvent) {
    GitBrancher.getInstance(myProject).rebase(listOf(repository), hash.asString())
  }
}
