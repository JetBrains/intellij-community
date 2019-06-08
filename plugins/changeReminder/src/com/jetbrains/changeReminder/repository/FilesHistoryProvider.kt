// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.repository

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.jetbrains.changeReminder.processCommitsFromHashes
import com.jetbrains.changeReminder.retainAll

data class Commit(val id: Int, val time: Long, val author: String, val files: Set<FilePath>)

internal class FilesHistoryProvider(private val project: Project, private val dataGetter: IndexDataGetter) {
  private val filesHistoryCache = HashMap<FilePath, Collection<Int>>()

  private fun getCommitHashesWithFile(file: FilePath): Collection<Int> {
    val structureFilter = VcsLogFilterObject.fromPaths(setOf(file))
    return dataGetter.filter(listOf(structureFilter))
  }

  private fun getCommitsData(root: VirtualFile, commits: Collection<Int>): Collection<Commit> {
    val hashes = mutableMapOf<String, Int>()
    for (commit in commits) {
      val commitId = dataGetter.logStorage.getCommitId(commit) ?: continue
      val hash = commitId.takeIf { commitId.root == root }?.hash?.asString() ?: continue
      hashes[hash] = commit
    }

    val commitsData = mutableSetOf<Commit>()

    processCommitsFromHashes(project, root, hashes.keys.toList()) consume@{ commit ->
      if (commit.changes.isNotEmpty()) {
        val id = hashes[commit.id.asString()] ?: return@consume
        val time = commit.commitTime
        val files = commit.changes.mapNotNull { ChangesUtil.getFilePath(it) }.toSet()
        val author = commit.author
        commitsData.add(Commit(id, time, author.name, files))
      }
    }
    return commitsData
  }

  fun getFilesHistory(root: VirtualFile, files: Collection<FilePath>): Collection<Commit> {
    filesHistoryCache.retainAll(files)

    filesHistoryCache.putAll(files.filter { it !in filesHistoryCache }
                               .associateWith { getCommitHashesWithFile(it) })
    val commits = files.mapNotNull { filesHistoryCache[it] }.flatten()

    return getCommitsData(root, commits)
  }

  fun clear() {
    filesHistoryCache.clear()
  }
}