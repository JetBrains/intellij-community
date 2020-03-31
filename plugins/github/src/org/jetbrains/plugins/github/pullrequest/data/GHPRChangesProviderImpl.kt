// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.PatchReader
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
import java.util.*
import kotlin.collections.LinkedHashMap

class GHPRChangesProviderImpl(private val repository: GitRepository,
                              mergeBaseRef: String,
                              commitsWithDiffs: List<Triple<GHCommit, String, String>>)
  : GHPRChangesProvider {

  override val changes: List<Change>
  override val changesByCommits: Map<GHCommit, List<Change>>

  private val diffDataByChange: Map<Change, GHPRChangeDiffData>

  init {
    changesByCommits = LinkedHashMap()
    diffDataByChange = THashMap(object : TObjectHashingStrategy<Change> {
      override fun equals(o1: Change?, o2: Change?) = o1 == o2 &&
                                                      o1?.beforeRevision == o2?.beforeRevision &&
                                                      o2?.afterRevision == o2?.afterRevision

      override fun computeHashCode(change: Change?) = Objects.hash(change, change?.beforeRevision, change?.afterRevision)
    })

    val fileHistoriesByLastKnownFilePath = mutableMapOf<String, GHPRChangeDiffData.FileHistory>()

    var lastCommitSha = mergeBaseRef
    var lastCumulativePatches: List<FilePatch>? = null

    val commitsHashes = commitsWithDiffs.map { it.first.oid }
    for ((commit, commitDiff, diffFromMergeBase) in commitsWithDiffs) {

      val commitSha = commit.oid
      val commitChanges = mutableListOf<Change>()
      val commitPatches = readAllPatches(commitDiff)

      val cumulativePatches = readAllPatches(diffFromMergeBase)

      for (patch in commitPatches) {
        val change = createChangeFromPatch(lastCommitSha, commitSha, patch)
        commitChanges.add(change)

        if (patch is TextFilePatch) {
          val beforePath = patch.beforeName
          val afterPath = patch.afterName

          val historyBefore = beforePath?.let { fileHistoriesByLastKnownFilePath.remove(it) }
          val fileHistory = (historyBefore ?: GHPRChangeDiffData.FileHistory(commitsHashes)).apply {
            append(commitSha, patch)
          }
          fileHistoriesByLastKnownFilePath[afterPath] = fileHistory
          val initialPath = fileHistory.initialFilePath

          val cumulativePatch = findPatchByFilePaths(cumulativePatches, initialPath, afterPath) as? TextFilePatch
          if (cumulativePatch == null) {
            LOG.debug("Unable to find cumulative patch for commit patch")
            continue
          }

          diffDataByChange[change] = GHPRChangeDiffData.Commit(commitSha, patch.filePath,
                                                               patch, cumulativePatch,
                                                               fileHistory)
        }
      }
      changesByCommits[commit] = commitChanges
      lastCommitSha = commitSha
      lastCumulativePatches = cumulativePatches
    }

    changes = mutableListOf()

    val fileHistoriesBySummaryFilePath = fileHistoriesByLastKnownFilePath.mapKeys {
      it.value.filePath
    }

    lastCumulativePatches?.let {
      for (patch in it) {
        val change = createChangeFromPatch(mergeBaseRef, lastCommitSha, patch)
        changes.add(change)

        if (patch is TextFilePatch) {
          val filePath = patch.filePath
          val fileHistory = fileHistoriesBySummaryFilePath[filePath]
          if (fileHistory == null) {
            LOG.debug("Unable to find file history for cumulative patch for $filePath")
            continue
          }

          diffDataByChange[change] = GHPRChangeDiffData.Cumulative(lastCommitSha, filePath,
                                                                   patch, fileHistory)
        }
      }
    }
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

  override fun findChangeDiffData(change: Change) = diffDataByChange[change]

  companion object {
    private val LOG = logger<GHPRChangesProvider>()

    private val TextFilePatch.filePath
      get() = (afterName ?: beforeName)!!

    private fun readAllPatches(diffFile: String): List<FilePatch> {
      val reader = PatchReader(diffFile, true)
      reader.parseAllPatches()
      return reader.allPatches
    }
  }
}