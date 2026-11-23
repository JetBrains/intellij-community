// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.RefComparisonChange
import com.intellij.openapi.vcs.history.ShortVcsRevisionNumber
import java.util.Collections
import java.util.Objects

internal class GitBranchComparisonResultImpl(
  override val baseSha: String,
  override val mergeBaseSha: String,
  override val commits: List<GitCommitShaWithPatches>,
  private val headPatches: List<FilePatch>,
  private val createRevisionNumber: (String) -> ShortVcsRevisionNumber,
  private val createFilePath: (String) -> FilePath,
) : GitBranchComparisonResult {

  override val headSha: String = commits.last().sha

  private val _changes = mutableListOf<RefComparisonChange>()
  override val changes: List<RefComparisonChange> = Collections.unmodifiableList(_changes)
  private val _changesByCommits = mutableMapOf<String, List<RefComparisonChange>>()
  override val changesByCommits: Map<String, List<RefComparisonChange>> = Collections.unmodifiableMap(_changesByCommits)

  private val _diffDataByChange: MutableMap<RefComparisonChange, GitTextFilePatchWithHistory> = mutableMapOf()
  override val patchesByChange: Map<RefComparisonChange, GitTextFilePatchWithHistory> = Collections.unmodifiableMap(_diffDataByChange)

  init {
    try {
      initForLinearHistory(commits)
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
      val commitChanges = mutableListOf<RefComparisonChange>()

      if (commitWithPatches.parents.count { commitsHashes.contains(it) } <= 1) {
        for (patch in commitWithPatches.patches) {
          val change = createChangeFromPatch(previousCommitSha, commitSha, patch)
          commitChanges.add(change)

          if (patch is TextFilePatch) {
            val beforePath = patch.beforeName
            val afterPath = patch.afterName

            val historyBefore = beforePath?.let { fileHistoriesByLastKnownFilePath.remove(it) }
            val fileHistory = (historyBefore ?: startNewHistory(commitsHashes, previousCommitSha, beforePath)).apply {
              append(commitSha, patch)
            }
            val path = (afterPath ?: beforePath)!!
            fileHistoriesByLastKnownFilePath[path] = fileHistory

            patch.beforeVersionId = previousCommitSha
            patch.afterVersionId = commitSha
            _diffDataByChange[change] = GitTextFilePatchWithHistory(patch, false, fileHistory)
          }
          else {
            LOG.debug("Non-text file ${patch.filePath} patch encountered in a commit $commitSha diff")
          }
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
        val beforePath = patch.beforeName
        val afterPath = patch.afterName

        val filePath = (afterPath ?: beforePath)!!
        val fileHistory = fileHistoriesBySummaryFilePath[filePath] ?: run {
          LOG.warn("Unable to connect the cumulative patch for $filePath to the commit history")
          startNewHistory(commitsHashes, mergeBaseSha, beforePath).apply {
            append(headSha, afterPath)
          }
        }
        patch.beforeVersionId = baseSha
        patch.afterVersionId = headSha

        _diffDataByChange[change] = GitTextFilePatchWithHistory(patch, true, fileHistory)
      }
      else {
        LOG.debug("Non-text file ${patch.filePath} patch encountered in a cumulative diff")
      }
    }
  }

  private fun startNewHistory(commitsHashes: List<String>, startCommitSha: String, startFilePath: String?) =
    MutableLinearGitFileHistory(commitsHashes).apply {
      append(startCommitSha, startFilePath)
    }

  private fun createChangeFromPatch(beforeRef: String, afterRef: String, patch: FilePatch): RefComparisonChange {
    val beforePath = if (patch.isNewFile) null else createFilePath(patch.beforeName)
    val afterPath = if (patch.isDeletedFile) null else createFilePath(patch.afterName)
    return RefComparisonChange(createRevisionNumber(beforeRef), beforePath, createRevisionNumber(afterRef), afterPath)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitBranchComparisonResultImpl) return false
    return baseSha == other.baseSha && mergeBaseSha == other.mergeBaseSha && headSha == other.headSha &&
           changes == other.changes
  }

  override fun hashCode(): Int = Objects.hash(baseSha, mergeBaseSha, headSha)


  companion object {
    private val LOG = logger<GitBranchComparisonResult>()
  }
}

val FilePatch.filePath: String
  get() = (afterName ?: beforeName)!!