// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.diff.util.Range
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitCommit
import git4idea.GitContentRevision
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.util.GHPatchHunkUtil

class GHPRChangesProviderImpl(private val repository: GitRepository, commits: List<GitCommit>, diffFile: String)
  : GHPRChangesProvider {

  override val lastCommitSha = commits.last().id.asString()

  override val changes: List<Change>
  private val patchesByChanges: Map<Change, TextFilePatch>
  private val changesIndex: Map<Pair<String, String>, Change>

  init {
    val changesWithRenames = buildChangesWithRenames(commits)

    val patchReader = PatchReader(diffFile, true)
    patchReader.parseAllPatches()

    changes = mutableListOf()
    patchesByChanges = mutableMapOf()
    changesIndex = mutableMapOf()

    for (patch in patchReader.allPatches) {
      val changeWithRenames = findChangeForPatch(changesWithRenames, patch)
      if (changeWithRenames == null) {
        LOG.info("Can't find change for patch $patch")
        continue
      }

      val change = changeWithRenames.getVcsChange()
      if (change == null) {
        LOG.info("Empty VCS change for patch $patch")
        continue
      }

      changes.add(change)
      if (patch is TextFilePatch) {
        patchesByChanges[change] = patch
        for ((commit, filePath) in changeWithRenames.pathsByCommit) {
          changesIndex[commit to convertFilePath(filePath)] = change
        }
      }
    }
  }

  override fun getFilePath(change: Change) = convertFilePath(ChangesUtil.getFilePath(change))

  private fun convertFilePath(filePath: FilePath): String {
    val root = repository.root.path
    val file = filePath.path
    return FileUtil.getRelativePath(root, file, '/', true)
           ?: throw IllegalArgumentException("The file $filePath cannot be made relative to repository root $root")
  }

  private fun buildChangesWithRenames(commits: List<GitCommit>): Collection<MutableChange> {
    val changes = mutableMapOf<FilePath, MutableChange>()

    var previousCommitHash: String? = null
    for (commit in commits) {
      val commitHash = commit.id.asString()
      for (change in commit.changes) {
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

  override fun findDiffRanges(change: Change): List<Range>? {
    val patch = patchesByChanges[change] ?: return null
    return patch.hunks.map(GHPatchHunkUtil::getRange)
  }

  override fun findChange(commitSha: String, filePath: String) = changesIndex[commitSha to filePath]

  override fun findFileLinesMapper(change: Change) = patchesByChanges[change]?.let { GHPRChangedFileLinesMapperImpl(it) }

  override fun findDiffRangesWithoutContext(change: Change): List<Range>? {
    val patch = patchesByChanges[change] ?: return null
    return patch.hunks.map(GHPatchHunkUtil::getChangeOnlyRanges).flatten()
  }

  private fun findChangeForPatch(changes: Collection<MutableChange>, patch: FilePatch): MutableChange? {
    val beforeName = if (patch.isNewFile) null else patch.beforeName
    val afterName = if (patch.isDeletedFile) null else patch.afterName

    val beforePath = beforeName?.let { VcsUtil.getFilePath(repository.root, it) }
    val afterPath = afterName?.let { VcsUtil.getFilePath(repository.root, it) }

    return changes.find { it.firstRevision?.file == beforePath && it.lastRevision?.file == afterPath }
  }

  companion object {
    private val LOG = logger<GHPRChangesProvider>()
  }

  private class MutableChange {
    var firstRevision: GitContentRevision? = null
    var lastRevision: GitContentRevision? = null

    val pathsByCommit = mutableMapOf<String, FilePath>()

    fun getVcsChange(): Change? {
      if (firstRevision == null && lastRevision == null) return null
      return Change(firstRevision, lastRevision)
    }
  }
}