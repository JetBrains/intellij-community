// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitCommit
import git4idea.GitContentRevision
import git4idea.GitUtil
import git4idea.repo.GitRepository

class GHPRChangesProviderImpl(private val repository: GitRepository, commits: List<GitCommit>, diffFile: String)
  : GHPRChangesProvider {

  override val changes: List<Change>

  init {
    val changesWithRenames = buildChangesWithRenames(commits)

    val patchReader = PatchReader(diffFile, true)
    patchReader.parseAllPatches()

    changes = mutableListOf()

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
    }
  }

  private fun buildChangesWithRenames(commits: List<GitCommit>): Collection<MutableChange> {
    val changes = mutableMapOf<FilePath, MutableChange>()

    for (commit in commits) {
      for (change in commit.changes) {
        when (change.type) {
          Change.Type.NEW -> {
            val afterRevision = change.afterRevision as GitContentRevision
            changes.getOrPut(afterRevision.file, ::MutableChange).apply {
              firstRevision = null
              lastRevision = afterRevision
            }
          }
          Change.Type.DELETED -> {
            val beforeRevision = change.beforeRevision as GitContentRevision
            changes.getOrPut(beforeRevision.file, ::MutableChange).apply {
              firstRevision = beforeRevision
              lastRevision = null
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
              }
            changes[afterRevision.file] = mutableChange
          }
        }
      }
    }
    return changes.values
  }

  private fun findChangeForPatch(changes: Collection<MutableChange>, patch: FilePatch): MutableChange? {
    var beforeName = patch.beforeName
    var afterName = patch.afterName
    //workaround for empty "binary patches"
    if (patch is TextFilePatch && patch.hunks.isEmpty()) {
      if (patch.beforeVersionId == "0000000") beforeName = null
      if (patch.afterVersionId == "0000000") afterName = null
    }
    else {
      if (patch.isNewFile) beforeName = null
      if (patch.isDeletedFile) afterName = null
    }

    val beforePath = beforeName?.let { VcsUtil.getFilePath(repository.root, GitUtil.unescapePath(it)) }
    val afterPath = afterName?.let { VcsUtil.getFilePath(repository.root, GitUtil.unescapePath(it)) }

    return changes.find { it.firstRevision?.file == beforePath && it.lastRevision?.file == afterPath }
  }

  companion object {
    private val LOG = logger<GHPRChangesProvider>()
  }

  private class MutableChange {
    var firstRevision: GitContentRevision? = null
    var lastRevision: GitContentRevision? = null

    fun getVcsChange(): Change? {
      if (firstRevision == null && lastRevision == null) return null
      return Change(firstRevision, lastRevision)
    }
  }
}