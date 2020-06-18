// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.graph.utils.DfsWalk
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.GitUtil
import git4idea.findProtectedRemoteBranch
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls

internal abstract class GitCommitEditingActionBase<T : GitCommitEditingActionBase.MultipleCommitEditingData> : DumbAwareAction() {
  companion object {
    internal fun findContainingBranches(data: VcsLogData, root: VirtualFile, hash: Hash): List<String> {
      val branchesGetter = data.containingBranchesGetter
      val branches = branchesGetter.getContainingBranchesQuickly(root, hash)
      if (branches == null) {
        return ProgressManager.getInstance()
          .runProcessWithProgressSynchronously<List<String>, RuntimeException>(
            {
              branchesGetter.getContainingBranchesSynchronously(root, hash)
            },
            GitBundle.getString("rebase.log.commit.editing.action.progress.containing.branches.title"),
            true,
            data.project
          )
      }
      return branches
    }
  }

  protected open val prohibitRebaseDuringRebasePolicy: ProhibitRebaseDuringRebasePolicy = ProhibitRebaseDuringRebasePolicy.Allow

  protected abstract fun actionPerformedAfterChecks(commitEditingData: T)

  protected abstract fun getFailureTitle(): String

  protected abstract fun createCommitEditingData(
    repository: GitRepository,
    log: VcsLog,
    logData: VcsLogData,
    logUi: VcsLogUi
  ): T?

  protected open fun update(e: AnActionEvent, commitEditingData: T) {
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isEnabledAndVisible = false

    val commitEditingData = createCommitEditingData(e) ?: return

    e.presentation.isVisible = true

    val commitList = commitEditingData.selectedCommitList
    val repository = commitEditingData.repository

    if (VcsLogUtil.findBranch(commitEditingData.logData.dataPack.refsModel, repository.root, GitUtil.HEAD) == null) {
      e.presentation.description = GitBundle.message("rebase.log.multiple.commit.editing.action.cant.find.head", commitList.size)
      return
    }

    // editing merge commit or root commit is not allowed
    commitList.forEach { commit ->
      if (commit.isRootOrMerge()) {
        e.presentation.description = GitBundle.message("rebase.log.commit.editing.action.disabled.parents.description", commit.parents.size)
        return
      }
    }

    // check that first and last selected commits are in HEAD and not pushed to protected branch
    listOf(commitList.first(), commitList.last()).forEach { commit ->
      val branches = commitEditingData.log.getContainingBranches(commit.id, commit.root)
      if (branches != null) { // otherwise the information is not available yet, and we'll recheck harder in actionPerformed
        if (GitUtil.HEAD !in branches) {
          e.presentation.description = GitBundle.getString("rebase.log.commit.editing.action.commit.not.in.head.error.text")
          return
        }

        // and not if pushed to a protected branch
        val protectedBranch = findProtectedRemoteBranch(repository, branches)
        if (protectedBranch != null) {
          e.presentation.description = GitBundle.message(
            "rebase.log.commit.editing.action.commit.pushed.to.protected.branch.error.text",
            protectedBranch
          )
          return
        }
      }
    }

    when (val policy = prohibitRebaseDuringRebasePolicy) {
      ProhibitRebaseDuringRebasePolicy.Allow -> {
      }
      is ProhibitRebaseDuringRebasePolicy.Prohibit -> {
        val message = getProhibitedStateMessage(commitEditingData, policy.operation)
        if (message != null) {
          e.presentation.description = message
          return
        }
      }
    }

    e.presentation.isEnabledAndVisible = true
    update(e, commitEditingData)
  }

  private fun VcsShortCommitDetails.isRootOrMerge() = parents.size != 1

  final override fun actionPerformed(e: AnActionEvent) {
    val commitEditingRequirements = createCommitEditingData(e)!!
    val description = checkCommitsEditingAvailability(commitEditingRequirements)

    if (description != null) {
      Messages.showErrorDialog(
        commitEditingRequirements.project,
        description,
        getFailureTitle()
      )
      return
    }
    actionPerformedAfterChecks(commitEditingRequirements)
  }

  protected open fun checkCommitsEditingAvailability(commitEditingData: T): String? {
    val description = checkHeadLinearHistory(commitEditingData)
    if (description != null) {
      return description
    }

    // if any commit is pushed to protected branch, the last (oldest) commit is published as well => it is enough to check only the last.
    val lastCommit = commitEditingData.selectedCommitList.last()
    val branches = findContainingBranches(commitEditingData.logData, lastCommit.root, lastCommit.id)
    val protectedBranch = findProtectedRemoteBranch(commitEditingData.repository, branches)
    if (protectedBranch != null) {
      return GitBundle.message("rebase.log.commit.editing.action.commit.pushed.to.protected.branch.error.text", protectedBranch)
    }

    return null
  }

  private fun getCommitIdByNodeId(data: VcsLogData, permanentGraph: PermanentGraphImpl<Int>, nodeId: Int): CommitId =
    data.getCommitId(permanentGraph.permanentCommitsInfo.getCommitId(nodeId))!!

  /**
   * Check that a path which contains selected commits and doesn't contain merge commits exists in HEAD
   */
  private fun checkHeadLinearHistory(commitEditingData: MultipleCommitEditingData): String? {
    val project = commitEditingData.project
    val root = commitEditingData.repository.root
    val logData = commitEditingData.logData
    val dataPack = logData.dataPack
    val commits = commitEditingData.selectedCommitList
    val permanentGraph = dataPack.permanentGraph as PermanentGraphImpl<Int>
    val commitsInfo = permanentGraph.permanentCommitsInfo
    val commitIndices = commits.map { logData.getCommitIndex(it.id, root) }

    var description: String? = null

    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        val commitNodeIds = commitsInfo.convertToNodeIds(commitIndices).toMutableSet()
        val headRef = VcsLogUtil.findBranch(dataPack.refsModel, root, GitUtil.HEAD)!!
        val headIndex = logData.getCommitIndex(headRef.commitHash, root)
        val headId = commitsInfo.getNodeId(headIndex)
        val maxNodeId = commitNodeIds.max()!!

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
              description = GitBundle.message("rebase.log.multiple.commit.editing.action.specific.commit.not.in.head", commitNotInHead.hash)
              false
            }
            else -> {
              commitNodeIds.remove(nodeId)
              commitNodeIds.isNotEmpty()
            }
          }
        }
      },
      GitBundle.getString("rebase.log.multiple.commit.editing.action.progress.indicator.action.possibility.check"),
      true,
      project
    )
    return description
  }

  private fun createCommitEditingData(e: AnActionEvent): T? {
    val project = e.project ?: return null
    val log = e.getData(VcsLogDataKeys.VCS_LOG) ?: return null
    val logDataProvider = e.getData(VcsLogDataKeys.VCS_LOG_DATA_PROVIDER) as VcsLogData? ?: return null
    val logUi = e.getData(VcsLogDataKeys.VCS_LOG_UI) ?: return null

    val commitList = log.selectedShortDetails.takeIf { it.isNotEmpty() } ?: return null
    val repositoryManager = GitUtil.getRepositoryManager(project)

    val root = commitList.map { it.root }.distinct().singleOrNull() ?: return null
    val repository = repositoryManager.getRepositoryForRootQuick(root) ?: return null
    if (repositoryManager.isExternal(repository)) {
      return null
    }

    return createCommitEditingData(repository, log, logDataProvider, logUi)
  }

  protected open fun getProhibitedStateMessage(
    commitEditingData: T,
    @Nls operation: String
  ): String? = when (commitEditingData.repository.state) {
    Repository.State.NORMAL, Repository.State.DETACHED -> null
    Repository.State.REBASING -> GitBundle.message("rebase.log.commit.editing.action.prohibit.state.rebasing", operation)
    Repository.State.MERGING -> GitBundle.message("rebase.log.commit.editing.action.prohibit.state.merging", operation)
    Repository.State.GRAFTING -> GitBundle.message("rebase.log.commit.editing.action.prohibit.state.grafting", operation)
    Repository.State.REVERTING -> GitBundle.message("rebase.log.commit.editing.action.prohibit.state.reverting", operation)
    else -> GitBundle.message("rebase.log.commit.editing.action.prohibit.state", operation)
  }

  open class MultipleCommitEditingData(
    val repository: GitRepository,
    val log: VcsLog,
    val logData: VcsLogData,
    val logUi: VcsLogUi
  ) {
    val project = repository.project
    val selectedCommitList: List<VcsShortCommitDetails> = log.selectedShortDetails
  }

  protected sealed class ProhibitRebaseDuringRebasePolicy {
    object Allow : ProhibitRebaseDuringRebasePolicy()
    class Prohibit(val operation: @Nls String) : ProhibitRebaseDuringRebasePolicy()
  }
}