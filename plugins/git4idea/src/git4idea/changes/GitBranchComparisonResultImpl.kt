// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitRevisionNumber
import java.util.*

internal class GitBranchComparisonResultImpl(
  private val project: Project,
  private val vcsRoot: VirtualFile,
  override val baseSha: String,
  override val mergeBaseSha: String,
  override val commits: List<GitCommitShaWithPatches>,
  private val headPatches: List<FilePatch>,
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
          LOG.warn("Unable to find file history for cumulative patch for $filePath")
          continue
        }
        patch.beforeVersionId = baseSha
        patch.afterVersionId = headSha

        _diffDataByChange[change] = GitTextFilePatchWithHistory(patch, true, fileHistory)
      }
    }
  }

  private fun startNewHistory(commitsHashes: List<String>, startCommitSha: String, startFilePath: String?) =
    MutableLinearGitFileHistory(commitsHashes).apply {
      append(startCommitSha, startFilePath)
    }

  private fun createChangeFromPatch(beforeRef: String, afterRef: String, patch: FilePatch): RefComparisonChange {
    val beforePath = if (patch.isNewFile) null else VcsUtil.getFilePath(vcsRoot, patch.beforeName)
    val afterPath = if (patch.isDeletedFile) null else VcsUtil.getFilePath(vcsRoot, patch.afterName)
    return RefComparisonChange(GitRevisionNumber(beforeRef), beforePath, GitRevisionNumber(afterRef), afterPath)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GitBranchComparisonResultImpl

    if (project != other.project) return false
    if (vcsRoot != other.vcsRoot) return false
    if (baseSha != other.baseSha) return false
    if (mergeBaseSha != other.mergeBaseSha) return false
    if (headSha != other.headSha) return false

    return true
  }

  override fun hashCode(): Int {
    var result = project.hashCode()
    result = 31 * result + vcsRoot.hashCode()
    result = 31 * result + baseSha.hashCode()
    result = 31 * result + mergeBaseSha.hashCode()
    result = 31 * result + headSha.hashCode()
    return result
  }


  companion object {
    private val LOG = logger<GitBranchComparisonResult>()
  }
}

val FilePatch.filePath: String
  get() = (afterName ?: beforeName)!!