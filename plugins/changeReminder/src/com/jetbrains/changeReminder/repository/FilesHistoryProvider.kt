// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.repository

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.GitCommit
import git4idea.GitVcs
import git4idea.history.GitLogUtil

data class Commit(val id: Int, val time: Long, val files: Set<FilePath>)

class FilesHistoryProvider(private val project: Project, private val root: VirtualFile, private val dataGetter: IndexDataGetter) {
  private fun getCommitHashesWithFile(file: FilePath): Collection<Int> {
    val structureFilter = VcsLogFilterObject.fromPaths(setOf(file))
    return dataGetter.filter(listOf(structureFilter))
  }

  private fun getFilesData(files: Collection<FilePath>): Map<FilePath, Collection<Int>> {
    val filesData = mutableMapOf<FilePath, Collection<Int>>()
    files.forEach {
      filesData[it] = getCommitHashesWithFile(it)
    }
    return filesData
  }

  private fun getCommitsData(commits: Collection<Int>): Map<Int, Commit> {
    val hashes = mutableMapOf<String, Int>()
    for (commit in commits) {
      val hash = dataGetter.logStorage.getCommitId(commit)?.hash?.asString() ?: continue
      hashes[hash] = commit
    }

    val commitsData = mutableMapOf<Int, Commit>()
    GitLogUtil.readFullDetailsForHashes(
      project,
      root,
      GitVcs.getInstance(project),
      Consumer<GitCommit> { commit ->
        if (commit != null && commit.changes.isNotEmpty()) {
          val id = hashes[commit.id.asString()] ?: return@Consumer
          val time = commit.commitTime
          val files = commit.changes
            .mapNotNull { it.afterRevision?.file ?: it.beforeRevision?.file }
            .toSet()
          commitsData[id] = Commit(id, time, files)
        }
      },
      hashes.keys.toList(),
      true,
      false,
      false,
      false,
      GitLogUtil.DiffRenameLimit.NO_RENAMES
    )

    return commitsData
  }

  private fun collectFilesHistory(
    filesData: Map<FilePath, Collection<Int>>,
    commitsData: Map<Int, Commit>
  ): Map<FilePath, Set<Commit>> {
    val filesHistory = mutableMapOf<FilePath, Set<Commit>>()
    for ((file, fileCommits) in filesData) {
      val commits = fileCommits.mapNotNull { commitsData[it] }.toSet()
      filesHistory[file] = commits
    }
    return filesHistory
  }

  fun getFilesHistory(files: Collection<FilePath>): Map<FilePath, Set<Commit>> {
    val filesData = getFilesData(files)
    val commits = filesData.values.flatten().toSet()
    val commitsData = getCommitsData(commits)

    return collectFilesHistory(filesData, commitsData)
  }
}