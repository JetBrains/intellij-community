// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vcs.VcsException
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsShortCommitDetails
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.graph.utils.DfsWalk
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.GitUtil.HEAD
import git4idea.GitVcs
import git4idea.branch.GitRebaseParams
import git4idea.history.GitLogUtil
import git4idea.i18n.GitBundle
import git4idea.rebase.*
import git4idea.rebase.interactive.dialog.GitInteractiveRebaseDialog
import git4idea.repo.GitRepository

private val LOG = logger("Git.Interactive.Rebase.Using.Log")

@VisibleForTesting
@Throws(CantRebaseUsingLogException::class)
internal fun getEntriesUsingLog(
  repository: GitRepository,
  commit: VcsShortCommitDetails,
  logData: VcsLogData
): List<GitRebaseEntryGeneratedUsingLog> {
  val project = repository.project
  val root = repository.root

  val dataPack = logData.dataPack
  val permanentGraph = dataPack.permanentGraph as PermanentGraphImpl<Int>
  val commitsInfo = permanentGraph.permanentCommitsInfo

  val headRef = VcsLogUtil.findBranch(dataPack.refsModel, root, HEAD)
                ?: throw CantRebaseUsingLogException(CantRebaseUsingLogException.Reason.UNRESOLVED_HEAD)
  val headIndex = logData.getCommitIndex(headRef.commitHash, root)
  val headId = commitsInfo.getNodeId(headIndex)

  val graph = LinearGraphUtils.asLiteLinearGraph(permanentGraph.linearGraph)
  val used = BitSetFlags(permanentGraph.linearGraph.nodesCount())
  val commits = mutableListOf<Hash>()
  DfsWalk(listOf(headId), graph, used).walk(true) { nodeId ->
    ProgressManager.checkCanceled()
    val parents = graph.getNodes(nodeId, LiteLinearGraph.NodeFilter.DOWN)
    // commit is not root or merge
    if (parents.size == 1) {
      val commitId = permanentGraph.permanentCommitsInfo.getCommitId(nodeId)
      val hash = logData.getCommitId(commitId)!!.hash
      commits.add(hash)
      hash != commit.id
    }
    else {
      throw CantRebaseUsingLogException(CantRebaseUsingLogException.Reason.MERGE)
    }
  }

  if (commits.last() != commit.id) {
    throw CantRebaseUsingLogException(CantRebaseUsingLogException.Reason.UNEXPECTED_HASH)
  }

  val details = try {
    GitLogUtil.collectMetadata(
      project,
      GitVcs.getInstance(project),
      repository.root,
      commits.map { it.asString() }
    )
  }
  catch (e: VcsException) {
    throw CantRebaseUsingLogException(CantRebaseUsingLogException.Reason.UNRESOLVED_HASH)
  }

  if (details.any { it.subject.startsWith("fixup!") || it.subject.startsWith("squash!") }) {
    throw CantRebaseUsingLogException(CantRebaseUsingLogException.Reason.FIXUP_SQUASH)
  }

  return details.map { GitRebaseEntryGeneratedUsingLog(it) }.reversed()
}

internal fun interactivelyRebaseUsingLog(repository: GitRepository, commit: VcsShortCommitDetails, logData: VcsLogData) {
  val project = repository.project
  val root = repository.root

  object : Task.Backgroundable(project, GitBundle.message("rebase.progress.indicator.preparing.title")) {
    private var generatedEntries: List<GitRebaseEntryGeneratedUsingLog>? = null

    override fun run(indicator: ProgressIndicator) {
      try {
        generatedEntries = getEntriesUsingLog(repository, commit, logData)
      }
      catch (e: CantRebaseUsingLogException) {
        LOG.warn("Couldn't use log for rebasing: ${e.message}")
      }
    }

    override fun onSuccess() {
      generatedEntries?.let { entries ->
        val dialog = GitInteractiveRebaseDialog(project, root, entries)
        dialog.show()
        if (dialog.isOK) {
          startInteractiveRebase(repository, commit, GitInteractiveRebaseUsingLogEditorHandler(repository, entries, dialog.getModel()))
        }
      } ?: startInteractiveRebase(repository, commit)
    }
  }.queue()
}

internal fun startInteractiveRebase(
  repository: GitRepository,
  commit: VcsShortCommitDetails,
  editorHandler: GitRebaseEditorHandler? = null
) {
  object : Task.Backgroundable(repository.project, GitBundle.message("rebase.progress.indicator.title")) {
    override fun run(indicator: ProgressIndicator) {
      val params = GitRebaseParams.editCommits(repository.vcs.version, commit.parents.first().asString(), editorHandler, false)
      GitRebaseUtils.rebase(repository.project, listOf(repository), params, indicator)
    }
  }.queue()
}

private class GitInteractiveRebaseUsingLogEditorHandler(
  repository: GitRepository,
  private val entriesGeneratedUsingLog: List<GitRebaseEntryGeneratedUsingLog>,
  private val rebaseTodoModel: GitRebaseTodoModel<GitRebaseEntryGeneratedUsingLog>
) : GitInteractiveRebaseEditorHandler(repository.project, repository.root) {
  private var rebaseFailed = false

  override fun collectNewEntries(entries: List<GitRebaseEntry>): List<GitRebaseEntry>? {
    if (rebaseFailed) {
      return super.collectNewEntries(entries)
    }
    entriesGeneratedUsingLog.forEachIndexed { i, generatedEntry ->
      val realEntry = entries[i]
      if (!generatedEntry.equalsWithReal(realEntry)) {
        myRebaseEditorShown = false
        rebaseFailed = true
        LOG.error(
          "Incorrect git-rebase-todo file was generated",
          Attachment("generated.txt", entriesGeneratedUsingLog.joinToString("\n")),
          Attachment("expected.txt", entries.joinToString("\n"))
        )
        throw VcsException("Couldn't start Rebase using Log")
      }
    }
    processModel(rebaseTodoModel)
    return rebaseTodoModel.convertToEntries()
  }
}

@VisibleForTesting
internal class CantRebaseUsingLogException(val reason: Reason) : Exception(reason.toString()) {
  enum class Reason {
    UNRESOLVED_HEAD,
    MERGE,
    FIXUP_SQUASH,
    UNEXPECTED_HASH,
    UNRESOLVED_HASH
  }
}

@VisibleForTesting
internal class GitRebaseEntryGeneratedUsingLog(details: VcsCommitMetadata) :
  GitRebaseEntryWithDetails(GitRebaseEntry(Action.PICK, details.id.asString(), details.subject), details) {

  fun equalsWithReal(realEntry: GitRebaseEntry) =
    action == realEntry.action &&
    commit.startsWith(realEntry.commit) &&
    subject == realEntry.subject
}