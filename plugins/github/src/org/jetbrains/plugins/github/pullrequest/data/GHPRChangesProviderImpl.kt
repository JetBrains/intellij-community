// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.google.common.graph.Graph
import com.google.common.graph.GraphBuilder
import com.google.common.graph.ImmutableGraph
import com.google.common.graph.Traverser
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import git4idea.repo.GitRepository
import gnu.trove.THashMap
import gnu.trove.TObjectHashingStrategy
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHCommitHash
import java.util.*
import kotlin.collections.LinkedHashMap

class GHPRChangesProviderImpl(private val repository: GitRepository,
                              private val mergeBaseRef: String,
                              commitsWithPatches: List<GHCommitWithPatches>)
  : GHPRChangesProvider {

  override val changes = mutableListOf<Change>()
  override val changesByCommits = LinkedHashMap<GHCommit, List<Change>>()

  private val diffDataByChange = THashMap<Change, GHPRChangeDiffData>(object : TObjectHashingStrategy<Change> {
    override fun equals(o1: Change?, o2: Change?) = o1 == o2 &&
                                                    o1?.beforeRevision == o2?.beforeRevision &&
                                                    o1?.afterRevision == o2?.afterRevision

    override fun computeHashCode(change: Change?) = Objects.hash(change, change?.beforeRevision, change?.afterRevision)
  })

  override fun findChangeDiffData(change: Change) = diffDataByChange[change]

  init {
    val commitsBySha = mutableMapOf<String, GHCommitWithPatches>()
    val parentCommits = mutableSetOf<GHCommitHash>()

    for (commitWithPatches in commitsWithPatches) {
      val commit = commitWithPatches.commit
      commitsBySha[commit.oid] = commitWithPatches
      parentCommits.addAll(commit.parents)
    }

    // One or more merge commit for changes that are included into PR (master merges are ignored)
    val linearHistory = commitsWithPatches.all { commit ->
      commit.parents.count { commitsBySha.contains(it) } <= 1
    }

    // Last commit is a commit which is not a parent of any other commit
    // We start searching from the last hoping for some semblance of order
    val lastCommit = commitsWithPatches.findLast { !parentCommits.contains(it.commit) } ?: error("Could not determine last commit")

    fun ImmutableGraph.Builder<GHCommitWithPatches>.addCommits(commit: GHCommitWithPatches) {
      addNode(commit)
      for (parent in commit.parents) {
        val parentCommit = commitsBySha[parent]
        if (parentCommit != null) {
          putEdge(commit, parentCommit)
          addCommits(parentCommit)
        }
      }
    }

    val commitsGraph = GraphBuilder
      .directed()
      .allowsSelfLoops(false)
      .immutable<GHCommitWithPatches>()
      .apply {
        addCommits(lastCommit)
      }.build()

    if (linearHistory) {
      initForLinearHistory(commitsGraph, lastCommit)
    }
    else {
      initForHistoryWithMerges(commitsBySha, commitsGraph, lastCommit)
    }
  }

  private fun initForLinearHistory(commitsGraph: ImmutableGraph<GHCommitWithPatches>, lastCommit: GHCommitWithPatches) {
    val commitsWithPatches = Traverser.forGraph(commitsGraph).depthFirstPostOrder(lastCommit).map { it }
    val fileHistoriesByLastKnownFilePath = mutableMapOf<String, GHPRMutableLinearFileHistory>()

    var previousCommitSha = mergeBaseRef

    val commitsHashes = commitsWithPatches.map { it.sha }
    for (commitWithPatches in commitsWithPatches) {

      val commitSha = commitWithPatches.sha
      val commitChanges = mutableListOf<Change>()

      for (patch in commitWithPatches.commitPatches) {
        val change = createChangeFromPatch(previousCommitSha, commitSha, patch)
        commitChanges.add(change)

        if (patch is TextFilePatch) {
          val beforePath = patch.beforeName
          val afterPath = patch.afterName

          val historyBefore = beforePath?.let { fileHistoriesByLastKnownFilePath.remove(it) }
          val fileHistory = (historyBefore ?: GHPRMutableLinearFileHistory(commitsHashes)).apply {
            append(commitSha, patch)
          }
          if (afterPath != null) {
            fileHistoriesByLastKnownFilePath[afterPath] = fileHistory
          }
          val firstKnownPath = fileHistory.firstKnownFilePath

          val cumulativePatch = findPatchByFilePaths(commitWithPatches.cumulativePatches, firstKnownPath, afterPath) as? TextFilePatch
          if (cumulativePatch == null) {
            LOG.debug("Unable to find cumulative patch for commit patch")
            continue
          }

          diffDataByChange[change] = GHPRChangeDiffData.Commit(commitSha, patch.filePath, patch, cumulativePatch, fileHistory)
        }
      }
      changesByCommits[commitWithPatches.commit] = commitChanges
      previousCommitSha = commitSha
    }

    val fileHistoriesBySummaryFilePath = fileHistoriesByLastKnownFilePath.mapKeys {
      it.value.lastKnownFilePath
    }

    for (patch in lastCommit.cumulativePatches) {
      val change = createChangeFromPatch(mergeBaseRef, lastCommit.sha, patch)
      changes.add(change)

      if (patch is TextFilePatch) {
        val filePath = patch.filePath
        val fileHistory = fileHistoriesBySummaryFilePath[filePath]
        if (fileHistory == null) {
          LOG.debug("Unable to find file history for cumulative patch for $filePath")
          continue
        }

        diffDataByChange[change] = GHPRChangeDiffData.Cumulative(lastCommit.sha, filePath, patch, fileHistory)
      }
    }
  }

  private fun initForHistoryWithMerges(commitsBySha: Map<String, GHCommitWithPatches>,
                                       commitsGraph: Graph<GHCommitWithPatches>,
                                       lastCommit: GHCommitWithPatches) {
    for (commitWithPatches in Traverser.forGraph(commitsGraph).depthFirstPostOrder(lastCommit)) {
      val previousCommitSha = commitWithPatches.parents.find { commitsBySha.contains(it) } ?: mergeBaseRef
      val commitSha = commitWithPatches.sha
      val commitChanges = commitWithPatches.commitPatches.map { createChangeFromPatch(previousCommitSha, commitSha, it) }
      changesByCommits[commitWithPatches.commit] = commitChanges
    }

    for (patch in lastCommit.cumulativePatches) {
      val change = createChangeFromPatch(mergeBaseRef, lastCommit.sha, patch)
      changes.add(change)

      if (patch is TextFilePatch) {
        diffDataByChange[change] = GHPRChangeDiffData.Cumulative(lastCommit.sha, patch.filePath, patch, LookupOnlyFileHistory(commitsBySha))
      }
    }
  }

  private class LookupOnlyFileHistory(private val commitsBySha: Map<String, GHCommitWithPatches>) : GHPRFileHistory {
    override fun contains(commitSha: String, filePath: String): Boolean {
      return commitsBySha[commitSha]?.cumulativePatches?.any { it.filePath == filePath } ?: false
    }

    override fun compare(commitSha1: String, commitSha2: String): Int = TODO("Not yet implemented")

    override fun getPatches(parent: String,
                            child: String,
                            includeFirstKnownPatch: Boolean,
                            includeLastPatch: Boolean): List<TextFilePatch> = TODO("Not yet implemented")

  }

  private fun createChangeFromPatch(beforeRef: String, afterRef: String, patch: FilePatch): Change {
    val project = repository.project
    val (beforePath, afterPath) = getPatchPaths(patch)
    val beforeRevision = beforePath?.let { GitContentRevision.createRevision(it, GitRevisionNumber(beforeRef), project) }
    val afterRevision = afterPath?.let { GitContentRevision.createRevision(it, GitRevisionNumber(afterRef), project) }

    return Change(beforeRevision, afterRevision)
  }

  private fun getPatchPaths(patch: FilePatch): Pair<FilePath?, FilePath?> {
    val beforeName = if (patch.isNewFile) null else patch.beforeName
    val afterName = if (patch.isDeletedFile) null else patch.afterName

    return beforeName?.let { VcsUtil.getFilePath(repository.root, it) } to afterName?.let { VcsUtil.getFilePath(repository.root, it) }
  }

  private fun findPatchByFilePaths(patches: Collection<FilePatch>, beforePath: String?, afterPath: String?): FilePatch? =
    patches.find { (afterPath != null && it.afterName == afterPath) || (afterPath == null && it.beforeName == beforePath) }

  companion object {
    private val LOG = logger<GHPRChangesProvider>()

    private val FilePatch.filePath
      get() = (afterName ?: beforeName)!!
  }
}