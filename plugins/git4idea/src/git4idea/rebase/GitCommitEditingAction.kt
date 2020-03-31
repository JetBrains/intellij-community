// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.dvcs.repo.Repository.State.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogData
import git4idea.GitUtil.HEAD
import git4idea.GitUtil.getRepositoryManager
import git4idea.findProtectedRemoteBranch
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.Nls

/**
 * Base class for Git action which is going to edit existing commits,
 * i.e. should be enabled only on commits not pushed to a protected branch.
 */
abstract class GitCommitEditingAction : DumbAwareAction() {

  private val LOG = logger<GitCommitEditingAction>()

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    val log = e.getData(VcsLogDataKeys.VCS_LOG)
    val data = e.getData(VcsLogDataKeys.VCS_LOG_DATA_PROVIDER) as VcsLogData?
    val ui = e.getData(VcsLogDataKeys.VCS_LOG_UI)
    if (project == null || log == null || data == null || ui == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val selectedCommits = log.selectedShortDetails.size
    if (selectedCommits != 1) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val commit = log.selectedShortDetails[0]
    val repositoryManager = getRepositoryManager(project)
    val repository = repositoryManager.getRepositoryForRootQuick(commit.root)
    if (repository == null || repositoryManager.isExternal(repository)) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    // editing merge commit or root commit is not allowed
    val parents = commit.parents.size
    if (parents != 1) {
      e.presentation.isEnabled = false
      e.presentation.description = GitBundle.message("rebase.log.commit.editing.action.disabled.parents.description", parents)
      return
    }

    // allow editing only in the current branch
    val branches = log.getContainingBranches(commit.id, commit.root)
    if (branches != null) { // otherwise the information is not available yet, and we'll recheck harder in actionPerformed
      if (!branches.contains(HEAD)) {
        e.presentation.isEnabled = false
        e.presentation.description = GitBundle.getString("rebase.log.commit.editing.action.commit.not.in.head.error.text")
        return
      }

      // and not if pushed to a protected branch
      val protectedBranch = findProtectedRemoteBranch(repository, branches)
      if (protectedBranch != null) {
        e.presentation.isEnabled = false
        e.presentation.description = commitPushedToProtectedBranchError(protectedBranch)
        return
      }
    }

    e.presentation.isEnabledAndVisible = true
  }

  final override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val data = e.getRequiredData(VcsLogDataKeys.VCS_LOG_DATA_PROVIDER) as VcsLogData
    val log = e.getRequiredData(VcsLogDataKeys.VCS_LOG)

    val commit = log.selectedShortDetails[0]
    val repository = getRepositoryManager(project).getRepositoryForRootQuick(commit.root)!!

    val branches = findContainingBranches(data, commit.root, commit.id)

    if (!branches.contains(HEAD)) {
      Messages.showErrorDialog(project, GitBundle.getString("rebase.log.commit.editing.action.commit.not.in.head.error.text"),
                               getFailureTitle())
      return
    }

    // and not if pushed to a protected branch
    val protectedBranch = findProtectedRemoteBranch(repository, branches)
    if (protectedBranch != null) {
      Messages.showErrorDialog(project, commitPushedToProtectedBranchError(protectedBranch), getFailureTitle())
      return
    }

    actionPerformedAfterChecks(e)
  }

  abstract fun actionPerformedAfterChecks(e: AnActionEvent)

  protected fun getLog(e: AnActionEvent): VcsLog = e.getRequiredData(VcsLogDataKeys.VCS_LOG)

  protected fun getLogData(e: AnActionEvent): VcsLogData = e.getRequiredData(VcsLogDataKeys.VCS_LOG_DATA_PROVIDER) as VcsLogData

  protected fun getUi(e: AnActionEvent): VcsLogUi = e.getRequiredData(VcsLogDataKeys.VCS_LOG_UI)

  protected fun getSelectedCommit(e: AnActionEvent): VcsShortCommitDetails = getLog(e).selectedShortDetails[0]!!

  @CalledInAny
  protected fun getRepository(e: AnActionEvent): GitRepository = getRepositoryManager(e.project!!).getRepositoryForRootQuick(getSelectedCommit(e).root)!!

  protected abstract fun getFailureTitle(): String

  protected fun findContainingBranches(data: VcsLogData, root: VirtualFile, hash: Hash): List<String> {
    val branchesGetter = data.containingBranchesGetter
    return branchesGetter.getContainingBranchesQuickly(root, hash) ?:
           ProgressManager.getInstance().runProcessWithProgressSynchronously<List<String>, RuntimeException>({
               branchesGetter.getContainingBranchesSynchronously(root, hash)
           }, GitBundle.getString("rebase.log.commit.editing.action.progress.containing.branches.title"), true, data.project)
  }

  protected fun commitPushedToProtectedBranchError(protectedBranch: String) =
    GitBundle.message("rebase.log.commit.editing.action.commit.pushed.to.protected.branch.error.text", protectedBranch)

  protected fun prohibitRebaseDuringRebase(e: AnActionEvent, @Nls operation: String, allowRebaseIfHeadCommit: Boolean = false) {
    if (e.presentation.isEnabledAndVisible) {
      val message = getProhibitedStateMessage(e, operation, allowRebaseIfHeadCommit)
      if (message != null) {
        e.presentation.isEnabled = false
        e.presentation.description = message
      }
    }
  }

  protected fun getProhibitedStateMessage(e: AnActionEvent, @Nls operation: String, allowRebaseIfHeadCommit: Boolean = false): String? {
    val state = getRepository(e).state
    if (state == NORMAL || state == DETACHED) {
      return null
    }
    if (state == REBASING && allowRebaseIfHeadCommit && isHeadCommit(e)) {
      return null
    }

    return when (state) {
      REBASING -> GitBundle.message("rebase.log.commit.editing.action.prohibit.state.rebasing", operation)
      MERGING -> GitBundle.message("rebase.log.commit.editing.action.prohibit.state.merging", operation)
      GRAFTING -> GitBundle.message("rebase.log.commit.editing.action.prohibit.state.grafting", operation)
      REVERTING -> GitBundle.message("rebase.log.commit.editing.action.prohibit.state.reverting", operation)
      else -> GitBundle.message("rebase.log.commit.editing.action.prohibit.state", operation)
    }
  }

  protected fun isHeadCommit(e: AnActionEvent): Boolean {
    return getSelectedCommit(e).id.asString() == getRepository(e).currentRevision
  }
}
