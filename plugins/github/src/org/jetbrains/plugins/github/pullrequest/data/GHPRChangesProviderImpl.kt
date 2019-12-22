// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import git4idea.repo.GitRepository
import gnu.trove.THashMap
import gnu.trove.TObjectHashingStrategy
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.util.GHPatchHunkUtil
import java.util.*
import kotlin.collections.LinkedHashMap

class GHPRChangesProviderImpl(private val repository: GitRepository,
                              mergeBaseRef: String,
                              commitsWithDiffs: List<Pair<GHCommit, String>>,
                              cumulativeDiff: String)
  : GHPRChangesProvider {

  override val changes: List<Change>
  override val changesByCommits: Map<GHCommit, List<Change>>

  private val diffDataByChange: Map<Change, GHPRChangesProvider.DiffData>

  init {
    changesByCommits = LinkedHashMap()
    diffDataByChange = THashMap(object : TObjectHashingStrategy<Change> {
      override fun equals(o1: Change?, o2: Change?) = o1 == o2 &&
                                                      o1?.beforeRevision == o2?.beforeRevision &&
                                                      o2?.afterRevision == o2?.afterRevision

      override fun computeHashCode(change: Change?) = Objects.hash(change, change?.beforeRevision, change?.afterRevision)
    })

    var lastCommitSha = mergeBaseRef

    for ((commit, commitDiff) in commitsWithDiffs) {

      val commitSha = commit.oid
      val commitChanges = mutableListOf<Change>()
      val commitPatches = readAllPatches(commitDiff)

      for (patch in commitPatches) {
        val change = createChangeFromPatch(lastCommitSha, commitSha, patch)
        commitChanges.add(change)
      }
      changesByCommits[commit] = commitChanges
      lastCommitSha = commitSha
    }

    val changesWithRenames = buildChangesWithRenames(changesByCommits)

    val patchReader = PatchReader(cumulativeDiff, true)
    patchReader.parseAllPatches()

    changes = mutableListOf()

    for (patch in patchReader.allPatches) {
      val changeWithRenames = findChangeForPatch(changesWithRenames, patch)
      if (changeWithRenames == null) {
        LOG.info("Can't find change for patch $patch")
        continue
      }

      val headRef = commitsWithDiffs.last().first.oid
      val change = changeWithRenames.createVcsChange(repository.project, mergeBaseRef, headRef)
      if (change == null) {
        LOG.info("Empty VCS change for patch $patch")
        continue
      }

      changes.add(change)
      if (patch is TextFilePatch) {
        diffDataByChange[change] = DiffDataImpl(headRef,
                                                convertFilePath(ChangesUtil.getFilePath(change)),
                                                patch,
                                                changeWithRenames.pathsByCommit.mapValues {
                                                  convertFilePath(it.value)
                                                })
      }
    }
  }

  override fun findChangeDiffData(change: Change) = diffDataByChange[change]

  private fun convertFilePath(filePath: FilePath): String {
    val root = repository.root.path
    val file = filePath.path
    return FileUtil.getRelativePath(root, file, '/', true)
           ?: throw IllegalArgumentException("The file $filePath cannot be made relative to repository root $root")
  }

  private fun buildChangesWithRenames(commits: Map<GHCommit, List<Change>>): Collection<MutableChange> {
    val changes = mutableMapOf<FilePath, MutableChange>()

    var previousCommitHash: String? = null
    for ((commit, commitChanges) in commits) {
      val commitHash = commit.oid
      for (change in commitChanges) {
        when (change.type) {
          Change.Type.NEW -> {
            val afterRevision = change.afterRevision as GitContentRevision
            changes.getOrPut(afterRevision.file, ::MutableChange).apply {
              firstRevision = null
              lastRevision = afterRevision
              pathsByCommit[commitHash] = afterRevision.file
            }
          }
          Change.Type.DELETED -> {
            val beforeRevision = change.beforeRevision as GitContentRevision
            changes.getOrPut(beforeRevision.file, ::MutableChange).apply {
              firstRevision = beforeRevision
              lastRevision = null
              pathsByCommit[commitHash] = beforeRevision.file
            }
          }
          Change.Type.MODIFICATION -> {
            val beforeRevision = change.beforeRevision as GitContentRevision
            val afterRevision = change.afterRevision as GitContentRevision
            changes.getOrPut(afterRevision.file) {
              MutableChange().apply {
                firstRevision = beforeRevision
              }
            }.apply {
              lastRevision = afterRevision
              pathsByCommit[commitHash] = afterRevision.file
            }
          }
          Change.Type.MOVED -> {
            val beforeRevision = change.beforeRevision as GitContentRevision
            val afterRevision = change.afterRevision as GitContentRevision
            val mutableChange =
              (changes.remove(beforeRevision.file) ?: MutableChange().apply {
                firstRevision = beforeRevision
              }).apply {
                lastRevision = afterRevision
                pathsByCommit[commitHash] = afterRevision.file
              }
            changes[afterRevision.file] = mutableChange
          }
        }
      }

      if (previousCommitHash != null) {
        for (chain in changes.values) {
          if (chain.pathsByCommit[commitHash] == null) {
            val previousName = chain.pathsByCommit[previousCommitHash]
            if (previousName != null) {
              chain.pathsByCommit[commitHash] = previousName
            }
          }
        }
      }
      previousCommitHash = commitHash
    }
    return changes.values
  }

  private fun createChangeFromPatch(beforeRef: String, afterRef: String, patch: FilePatch): Change {
    val project = repository.project
    val (beforePath, afterPath) = getPatchPaths(patch)
    val beforeRevision = beforePath?.let { GitContentRevision.createRevision(it, GitRevisionNumber(beforeRef), project) }
    val afterRevision = afterPath?.let { GitContentRevision.createRevision(it, GitRevisionNumber(afterRef), project) }

    return Change(beforeRevision, afterRevision)
  }

  private fun findChangeForPatch(changes: Collection<MutableChange>, patch: FilePatch): MutableChange? {
    val (beforePath, afterPath) = getPatchPaths(patch)
    return changes.find { it.firstRevision?.file == beforePath && it.lastRevision?.file == afterPath }
  }

  private fun getPatchPaths(patch: FilePatch): Pair<FilePath?, FilePath?> {
    val beforeName = if (patch.isNewFile) null else patch.beforeName
    val afterName = if (patch.isDeletedFile) null else patch.afterName

    return beforeName?.let { VcsUtil.getFilePath(repository.root, it) } to afterName?.let { VcsUtil.getFilePath(repository.root, it) }
  }

  companion object {
    private val LOG = logger<GHPRChangesProvider>()

    private fun readAllPatches(diffFile: String): List<FilePatch> {
      val reader = PatchReader(diffFile, true)
      reader.parseAllPatches()
      return reader.allPatches
    }
  }

  private class MutableChange {
    var firstRevision: GitContentRevision? = null
    var lastRevision: GitContentRevision? = null

    val pathsByCommit = mutableMapOf<String, FilePath>()

    fun createVcsChange(project: Project, baseRef: String, headRef: String): Change? {
      if (firstRevision == null && lastRevision == null) return null
      val firstVcsRevision = firstRevision?.let { GitContentRevision.createRevision(it.file, GitRevisionNumber(baseRef), project) }
      val lastVcsRevision = lastRevision?.let { GitContentRevision.createRevision(it.file, GitRevisionNumber(headRef), project) }
      return Change(firstVcsRevision, lastVcsRevision)
    }
  }

  private class DiffDataImpl(override val commitSha: String,
                             override val filePath: String,
                             private val patch: TextFilePatch,
                             private val filePathsMap: Map<String, String>) : GHPRChangesProvider.DiffData {

    override val diffRanges by lazy(LazyThreadSafetyMode.NONE) {
      patch.hunks.map(GHPatchHunkUtil::getRange)
    }
    override val diffRangesWithoutContext by lazy(LazyThreadSafetyMode.NONE) {
      patch.hunks.map(GHPatchHunkUtil::getChangeOnlyRanges).flatten()
    }
    override val linesMapper by lazy(LazyThreadSafetyMode.NONE) {
      GHPRChangedFileLinesMapperImpl(patch)
    }

    override fun contains(commitSha: String, filePath: String) = filePathsMap[commitSha] == filePath
  }
}