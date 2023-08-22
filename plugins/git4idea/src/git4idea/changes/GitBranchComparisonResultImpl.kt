// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.collaboration.util.REVISION_COMPARISON_CHANGE_HASHING_STRATEGY
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.CollectionFactory
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import java.util.*

class GitBranchComparisonResultImpl(private val project: Project,
                                    private val vcsRoot: VirtualFile,
                                    override val baseSha: String,
                                    override val mergeBaseSha: String,
                                    override val commits: List<GitCommitShaWithPatches>,
                                    private val headPatches: List<FilePatch>)
  : GitBranchComparisonResult {

  override val headSha: String = commits.last().sha

  private val _changes = mutableListOf<Change>()
  override val changes: List<Change> = Collections.unmodifiableList(_changes)
  private val _changesByCommits = mutableMapOf<String, List<Change>>()
  override val changesByCommits: Map<String, List<Change>> = Collections.unmodifiableMap(_changesByCommits)

  private val _diffDataByChange: MutableMap<Change, GitTextFilePatchWithHistory> =
    CollectionFactory.createCustomHashingStrategyMap(REVISION_COMPARISON_CHANGE_HASHING_STRATEGY)
  override val patchesByChange: Map<Change, GitTextFilePatchWithHistory> = Collections.unmodifiableMap(_diffDataByChange)

  init {
    val commitsHashes = commits.mapTo(mutableSetOf()) { it.sha }

    // One or more merge commit for changes that are included into PR (master merges are ignored)
    val linearHistory = commits.all { commit ->
      commit.parents.count { commitsHashes.contains(it) } <= 1
    }

    try {
      if (linearHistory) {
        initForLinearHistory(commits)
      }
      else {
        initForHistoryWithMerges(commits)
      }
    }
    catch (e: Exception) {
      throw RuntimeException("Unable to build branch comparison result between $baseSha and $headSha via $mergeBaseSha - ${e.message}", e)
    }
  }

  private fun initForLinearHistory(commits: List<GitCommitShaWithPatches>) {
    val fileHistoriesByLastKnownFilePath = mutableMapOf<String, MutableLinearGitFileHistory>()

    var previousCommitSha = mergeBaseSha

    val commitsHashes = listOf(mergeBaseSha) + commits.map { it.sha }
    for (commitWithPatches in commits) {

      val commitSha = commitWithPatches.sha
      val commitChanges = mutableListOf<Change>()

      for (patch in commitWithPatches.patches) {
        val change = createChangeFromPatch(previousCommitSha, commitSha, patch)
        commitChanges.add(change)

        if (patch is TextFilePatch) {
          val beforePath = patch.beforeName
          val afterPath = patch.afterName

          val historyBefore = beforePath?.let { fileHistoriesByLastKnownFilePath.remove(it) }
          val fileHistory = (historyBefore ?: MutableLinearGitFileHistory(commitsHashes)).apply {
            append(commitSha, patch)
          }
          if (afterPath != null) {
            fileHistoriesByLastKnownFilePath[afterPath] = fileHistory
          }

          patch.beforeVersionId = previousCommitSha
          patch.afterVersionId = commitSha
          _diffDataByChange[change] = GitTextFilePatchWithHistory(patch, false, fileHistory)
        }
      }
      _changesByCommits[commitWithPatches.sha] = commitChanges
      previousCommitSha = commitSha
    }

    val fileHistoriesBySummaryFilePath = fileHistoriesByLastKnownFilePath.mapKeys {
      it.value.lastKnownFilePath
    }

    for (patch in headPatches) {
      val change = createChangeFromPatch(mergeBaseSha, headSha, patch)
      _changes.add(change)

      if (patch is TextFilePatch) {
        val filePath = patch.filePath
        val fileHistory = fileHistoriesBySummaryFilePath[filePath]
        if (fileHistory == null) {
          LOG.debug("Unable to find file history for cumulative patch for $filePath")
          continue
        }
        patch.beforeVersionId = baseSha
        patch.afterVersionId = headSha

        _diffDataByChange[change] = GitTextFilePatchWithHistory(patch, true, fileHistory)
      }
    }
  }

  private fun initForHistoryWithMerges(commits: List<GitCommitShaWithPatches>) {
    val commitsHashes = commits.mapTo(mutableSetOf()) { it.sha }
    for (commitWithPatches in commits) {
      val previousCommitSha = commitWithPatches.parents.find { commitsHashes.contains(it) } ?: mergeBaseSha
      val commitSha = commitWithPatches.sha
      val commitChanges = commitWithPatches.patches.map { createChangeFromPatch(previousCommitSha, commitSha, it) }
      _changesByCommits[commitWithPatches.sha] = commitChanges
    }

    for (patch in headPatches) {
      val change = createChangeFromPatch(mergeBaseSha, headSha, patch)
      _changes.add(change)

      if (patch is TextFilePatch) {
        patch.beforeVersionId = baseSha
        patch.afterVersionId = headSha
        _diffDataByChange[change] = GitTextFilePatchWithHistory(patch, true, SinglePatchGitFileHistory(patch))
      }
    }
  }

  private fun createChangeFromPatch(beforeRef: String, afterRef: String, patch: FilePatch): Change {
    val (beforePath, afterPath) = getPatchPaths(patch)
    val beforeRevision = beforePath?.let { GitContentRevision.createRevision(it, GitRevisionNumber(beforeRef), project) }
    val afterRevision = afterPath?.let { GitContentRevision.createRevision(it, GitRevisionNumber(afterRef), project) }

    return Change(beforeRevision, afterRevision)
  }

  private fun getPatchPaths(patch: FilePatch): Pair<FilePath?, FilePath?> {
    val beforeName = if (patch.isNewFile) null else patch.beforeName
    val afterName = if (patch.isDeletedFile) null else patch.afterName

    return beforeName?.let { VcsUtil.getFilePath(vcsRoot, it) } to afterName?.let { VcsUtil.getFilePath(vcsRoot, it) }
  }

  companion object {
    private val LOG = logger<GitBranchComparisonResult>()
  }
}

val FilePatch.filePath: String
  get() = (afterName ?: beforeName)!!