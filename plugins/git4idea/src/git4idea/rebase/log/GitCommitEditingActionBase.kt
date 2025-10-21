// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log

import com.intellij.CommonBundle
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.data.LoadingDetails
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.api.permanent.VcsLogGraphNodeId
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.graph.utils.DfsWalk
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.ui.table.size
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.GitUtil
import git4idea.findProtectedRemoteBranch
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitCommitEditingActionBase.CommitEditingDataCreationResult.Created
import git4idea.rebase.log.GitCommitEditingActionBase.CommitEditingDataCreationResult.Prohibited
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls

abstract class GitCommitEditingActionBase<T : GitCommitEditingActionBase.MultipleCommitEditingData> : DumbAwareAction() {
  companion object {
    fun findContainingBranches(data: VcsLogData, root: VirtualFile, hash: Hash): List<String> {
      val branches = findContainingBranchesQuickly(data, root, hash)
      if (branches == null) {
        val branchesGetter = data.containingBranchesGetter
        return ProgressManager.getInstance()
          .runProcessWithProgressSynchronously<List<String>, RuntimeException>(
            {
              branchesGetter.getContainingBranchesSynchronously(root, hash)
            },
            GitBundle.message("rebase.log.commit.editing.action.progress.containing.branches.title"),
            true,
            data.project
          )
      }
      return branches
    }

    fun findContainingBranchesQuickly(data: VcsLogData, root: VirtualFile, hash: Hash): List<String>? {
      val branchesGetter = data.containingBranchesGetter
      return branchesGetter.getContainingBranchesQuickly(root, hash)
    }

    /**
     * Check that a path which contains selected commits and doesn't contain merge commits exists in HEAD
     */
    @Nls
    fun checkHeadLinearHistory(commitEditingData: MultipleCommitEditingData, @NlsContexts.ProgressTitle progressText: String): String? {
      val project = commitEditingData.project
      val root = commitEditingData.repository.root
      val logData = commitEditingData.logData
      val dataPack = logData.dataPack
      val permanentGraph = dataPack.permanentGraph as PermanentGraphImpl<VcsLogCommitStorageIndex>
      val commitsInfo = permanentGraph.permanentCommitsInfo
      val commitIndices = commitEditingData.selection.ids

      var description: String? = null

      ProgressManager.getInstance().runProcessWithProgressSynchronously(
        {
          val commitNodeIds = commitsInfo.convertToNodeIds(commitIndices).toMutableSet()
          val headRef = VcsLogUtil.findBranch(dataPack.refsModel, root, GitUtil.HEAD)!!
          val headIndex = logData.getCommitIndex(headRef.commitHash, root)
          val headId = commitsInfo.getNodeId(headIndex)
          val maxNodeId = commitNodeIds.maxOrNull()!!

          val graph = LinearGraphUtils.asLiteLinearGraph(permanentGraph.linearGraph)
          val used = BitSetFlags(permanentGraph.linearGraph.nodesCount())
          DfsWalk(listOf(headId), graph, used).walk(true) { nodeId ->
            ProgressManager.checkCanceled()
            val parents = graph.getNodes(nodeId, LiteLinearGraph.NodeFilter.DOWN)
            when {
              parents.size != 1 -> { // commit is root or merge
                val commit = getCommitIdByNodeId(logData, permanentGraph, nodeId)
                description = GitBundle.message(
                  "rebase.log.multiple.commit.editing.action.specific.commit.root.or.merge",
                  commit.hash,
                  parents.size
                )
                false
              }
              nodeId > maxNodeId -> { // we can no longer meet remaining selected commits below
                val commitNotInHead = getCommitIdByNodeId(logData, permanentGraph, commitNodeIds.first())
                description = GitBundle.message("rebase.log.multiple.commit.editing.action.specific.commit.not.in.head",
                                                commitNotInHead.hash)
                false
              }
              else -> {
                commitNodeIds.remove(nodeId)
                commitNodeIds.isNotEmpty()
              }
            }
          }
        }, progressText,
        true,
        project
      )
      return description
    }

    private fun getCommitIdByNodeId(data: VcsLogData, permanentGraph: PermanentGraphImpl<Int>, nodeId: VcsLogGraphNodeId): CommitId =
      data.getCommitId(permanentGraph.permanentCommitsInfo.getCommitId(nodeId))!!

  }

  protected open val prohibitRebaseDuringRebasePolicy: ProhibitRebaseDuringRebasePolicy = ProhibitRebaseDuringRebasePolicy.Allow

  protected abstract fun actionPerformedAfterChecks(commitEditingData: T)

  @Nls(capitalization = Nls.Capitalization.Title)
  protected abstract fun getFailureTitle(): String

  @Deprecated("Override createCommitEditingData(repository, selection, logData, selectedChanges) instead")
  protected open fun createCommitEditingData(
    repository: GitRepository,
    selection: VcsLogCommitSelection,
    logData: VcsLogData,
  ): CommitEditingDataCreationResult<T> {
    throw UnsupportedOperationException()
  }

  protected open fun createCommitEditingData(
    repository: GitRepository,
    selection: VcsLogCommitSelection,
    logData: VcsLogData,
    logUiEx: VcsLogUiEx?,
    selectedChanges: List<Change>,
  ): CommitEditingDataCreationResult<T> {
    return createCommitEditingData(repository, selection, logData)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  protected open fun update(e: AnActionEvent, commitEditingData: T) {
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = true
    e.presentation.description = templatePresentation.description

    val commitEditingDataCreationResult = createCommitEditingData(e)
    if (commitEditingDataCreationResult is Prohibited) {
      val description = commitEditingDataCreationResult.description
      if (description != null) {
        e.presentation.description = description
      }
      else {
        e.presentation.isVisible = false
      }
      e.presentation.isEnabled = false
      return
    }

    val commitEditingData = (commitEditingDataCreationResult as Created<T>).data
    val errorDescription = checkCommitsEditingAvailability(commitEditingData)
    if (errorDescription != null) {
      e.presentation.description = errorDescription
      e.presentation.isEnabled = false
      return
    }

    update(e, commitEditingData)
  }

  private fun checkCommitsEditingAvailability(commitEditingData: T): @Nls String? {
    return checkIsHeadBranch(commitEditingData)
           ?: checkNotMergeCommit(commitEditingData)
           ?: checkCommitsCanBeEdited(commitEditingData)
           ?: checkNotRebaseDuringRebase(commitEditingData)
  }

  private fun checkIsHeadBranch(commitEditingData: T): @Nls String? {
    val repository = commitEditingData.repository
    if (VcsLogUtil.findBranch(commitEditingData.logData.dataPack.refsModel, repository.root, GitUtil.HEAD) == null) {
      return GitBundle.message("rebase.log.multiple.commit.editing.action.cant.find.head", commitEditingData.selection.size)
    }
    return null
  }

  private fun checkNotInitialCommit(commitEditingData: T): Boolean {
    val commitList = commitEditingData.selection.cachedMetadata
    commitList.forEach { commit ->
      if (commit !is LoadingDetails && commit.parents.isEmpty()) {
        return false
      }
    }
    return true
  }

  protected open fun checkNotMergeCommit(commitEditingData: T): @Nls String? {
    val commitList = commitEditingData.selection.cachedMetadata
    commitList.forEach { commit ->
      if (commit !is LoadingDetails && commit.parents.size > 1) {
        return GitBundle.message("rebase.log.commit.editing.action.disabled.parents.description", commit.parents.size)
      }
    }
    return null
  }

  /**
   * Check that first and last selected commits are in HEAD and not pushed to protected branch
   */
  private fun checkCommitsCanBeEdited(commitEditingData: T): @Nls String? {
    val commitList = commitEditingData.selection.commits
    val repository = commitEditingData.repository
    listOf(commitList.first(), commitList.last()).forEach { commit ->
      val branches = commitEditingData.logData.containingBranchesGetter.getContainingBranchesQuickly(commit.root, commit.hash)
      if (branches != null) { // otherwise the information is not available yet, and we'll recheck harder in actionPerformed
        if (GitUtil.HEAD !in branches) {
          return GitBundle.message("rebase.log.commit.editing.action.commit.not.in.head.error.text")
        }

        // and not if pushed to a protected branch
        val protectedBranch = findProtectedRemoteBranch(repository, branches)
        if (protectedBranch != null) {
          return GitBundle.message("rebase.log.commit.editing.action.commit.pushed.to.protected.branch.error.text",
                                   protectedBranch
          )
        }
      }
    }
    return null
  }

  private fun checkNotRebaseDuringRebase(commitEditingData: T): @Nls String? {
    when (val policy = prohibitRebaseDuringRebasePolicy) {
      ProhibitRebaseDuringRebasePolicy.Allow -> {
      }
      is ProhibitRebaseDuringRebasePolicy.Prohibit -> {
        val message = getProhibitedStateMessage(commitEditingData, policy.operation)
        if (message != null) {
          return message
        }
      }
    }
    return null
  }

  final override fun actionPerformed(e: AnActionEvent) {
    val commitEditingRequirements = (createCommitEditingData(e) as Created<T>).data
    val description = lastCheckCommitsEditingAvailability(commitEditingRequirements)

    if (description != null) {
      Messages.showErrorDialog(
        commitEditingRequirements.project,
        description,
        getFailureTitle()
      )
      return
    }

    if (!checkNotInitialCommit(commitEditingRequirements)) {
      val ans = MessageDialogBuilder.Companion.yesNo(GitBundle.message("rebase.log.commit.editing.action.initial.commit.dialog.title"),
                                                     GitBundle.message("rebase.log.commit.editing.action.initial.commit.dialog.text"))
        .yesText(CommonBundle.getContinueButtonText())
        .noText(CommonBundle.getCancelButtonText())
        .icon(Messages.getWarningIcon())
        .ask(commitEditingRequirements.project)
      if (!ans) return
    }

    actionPerformedAfterChecks(commitEditingRequirements)
  }

  @Nls
  protected open fun lastCheckCommitsEditingAvailability(commitEditingData: T): String? {
    val description = checkHeadLinearHistory(
      commitEditingData,
      GitBundle.message("rebase.log.multiple.commit.editing.action.progress.indicator.action.possibility.check")
    )
    if (description != null) {
      return description
    }

    // if any commit is pushed to protected branch, the last (oldest) commit is published as well => it is enough to check only the last.
    val lastCommit = commitEditingData.selection.commits.last()
    val branches = findContainingBranches(commitEditingData.logData, lastCommit.root, lastCommit.hash)
    val protectedBranch = findProtectedRemoteBranch(commitEditingData.repository, branches)
    if (protectedBranch != null) {
      return GitBundle.message("rebase.log.commit.editing.action.commit.pushed.to.protected.branch.error.text", protectedBranch)
    }

    return null
  }

  private fun createCommitEditingData(e: AnActionEvent): CommitEditingDataCreationResult<T> {
    val project = e.project
    val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
    val logDataProvider = e.getData(VcsLogDataKeys.VCS_LOG_DATA_PROVIDER) as VcsLogData?
    val logUiEx = e.getData(VcsLogInternalDataKeys.LOG_UI_EX)
    val selectedChanges = e.getData(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS)?.toList() ?: emptyList()

    if (project == null || selection == null || logDataProvider == null) {
      return Prohibited()
    }

    val commitList = selection.commits.takeIf { it.isNotEmpty() } ?: return Prohibited()
    val repositoryManager = GitUtil.getRepositoryManager(project)

    val root = commitList.map { it.root }.distinct().singleOrNull() ?: return Prohibited(
      GitBundle.message("rebase.log.multiple.commit.editing.action.disabled.multiple.repository.description", commitList.size)
    )
    val repository = repositoryManager.getRepositoryForRootQuick(root) ?: return Prohibited()
    if (repositoryManager.isExternal(repository)) {
      return Prohibited(
        GitBundle.message("rebase.log.multiple.commit.editing.action.disabled.external.repository.description", commitList.size)
      )
    }

    return createCommitEditingData(repository, selection, logDataProvider, logUiEx, selectedChanges)
  }

  protected open fun getProhibitedStateMessage(
    commitEditingData: T,
    @Nls operation: String,
  ): @Nls String? = when (commitEditingData.repository.state) {
    Repository.State.NORMAL, Repository.State.DETACHED -> null
    Repository.State.REBASING -> GitBundle.message("rebase.log.commit.editing.action.prohibit.state.rebasing", operation)
    Repository.State.MERGING -> GitBundle.message("rebase.log.commit.editing.action.prohibit.state.merging", operation)
    Repository.State.GRAFTING -> GitBundle.message("rebase.log.commit.editing.action.prohibit.state.grafting", operation)
    Repository.State.REVERTING -> GitBundle.message("rebase.log.commit.editing.action.prohibit.state.reverting", operation)
    else -> GitBundle.message("rebase.log.commit.editing.action.prohibit.state", operation)
  }

  open class MultipleCommitEditingData @JvmOverloads constructor(
    val repository: GitRepository,
    val selection: VcsLogCommitSelection,
    val logData: VcsLogData,
    val logUiEx: VcsLogUiEx? = null,
  ) {
    val project = repository.project
  }

  protected sealed class ProhibitRebaseDuringRebasePolicy {
    object Allow : ProhibitRebaseDuringRebasePolicy()
    class Prohibit(@Nls val operation: String) : ProhibitRebaseDuringRebasePolicy()
  }

  protected sealed class CommitEditingDataCreationResult<T : MultipleCommitEditingData> {
    class Created<T : MultipleCommitEditingData>(val data: T) : CommitEditingDataCreationResult<T>()
    class Prohibited<T : MultipleCommitEditingData>(val description: @Nls String? = null) : CommitEditingDataCreationResult<T>()
  }
}