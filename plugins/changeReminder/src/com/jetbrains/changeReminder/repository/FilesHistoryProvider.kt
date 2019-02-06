// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.repository

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import java.util.*

data class Commit(val id: Int, val time: Long, val files: Set<FilePath>)

class FilesHistoryProvider(private val root: VirtualFile, private val dataGetter: IndexDataGetter) {
  private val commits = HashMap<Int, Commit>()

  private fun getCommitHashesWithFile(file: FilePath): Collection<Int> {
    val structureFilter = VcsLogFilterObject.fromPaths(setOf(file))
    return dataGetter.filter(listOf(structureFilter))
  }

  private fun getFileHistory(file: FilePath): Set<Commit> {
    val commitsSet = HashSet<Commit>()
    val commitHashes = getCommitHashesWithFile(file)

    for (commitId in commitHashes) {
      val commit = commits.getOrPut(commitId) {
        Commit(commitId, dataGetter.getCommitTime(commitId) ?: 0, dataGetter.getChangedPaths(commitId))
      }
      commitsSet.add(commit)
    }

    return commitsSet
  }

  fun getFilesHistory(files: Collection<FilePath>): Map<FilePath, Set<Commit>> {
    val currentCommit = HashMap<FilePath, Set<Commit>>()
    for (file in files) {
      currentCommit[file] = getFileHistory(file)
    }
    return currentCommit
  }

  override fun toString() = root.path
}