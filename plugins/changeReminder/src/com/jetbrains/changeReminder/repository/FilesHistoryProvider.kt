// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.repository

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.jetbrains.changeReminder.retainAll
import git4idea.history.GitCommitRequirements
import git4idea.history.GitHistoryTraverser
import git4idea.history.GitHistoryTraverserImpl
import git4idea.history.TraverseCommitId

data class Commit(val id: Int, val time: Long, val author: String, val files: Set<FilePath>)

internal class FilesHistoryProvider(
  private val project: Project,
  private val dataManager: VcsLogData,
  private val dataGetter: IndexDataGetter
) {
  companion object {
    private const val MAX_COMMITS_WITH_FILES = 1000
    private const val MAX_HISTORY_COMMIT_SIZE = 15
  }

  private val filesHistoryCache = HashMap<FilePath, Collection<TraverseCommitId>>()

  private fun getCommitsData(traverser: GitHistoryTraverser, files: Collection<FilePath>): Collection<Commit> {
    val commitsWithFiles = files.mapNotNull { filesHistoryCache[it] }.flatten()
    val commitsData = mutableSetOf<Commit>()

    val requirements = GitCommitRequirements(
      diffRenameLimit = GitCommitRequirements.DiffRenameLimit.NO_RENAMES,
      diffInMergeCommits = GitCommitRequirements.DiffInMergeCommits.NO_DIFF
    )
    var commitsWithFilesCount = 0
    traverser.traverseFromHead(GitHistoryTraverser.TraverseType.BFS) { commitId ->
      if (commitId in commitsWithFiles) {
        loadFullDetailsLater(commitId, requirements) { commit ->
          val affectedPaths = commit.affectedPaths
          if (affectedPaths.size in 1..MAX_HISTORY_COMMIT_SIZE) {
            commitsData.add(Commit(commitId, commit.commitTime, commit.author.name, affectedPaths))
          }
        }
        commitsWithFilesCount++
      }
      commitsWithFilesCount != MAX_COMMITS_WITH_FILES
    }
    return commitsData
  }

  fun getFilesHistory(root: VirtualFile, files: Collection<FilePath>): Collection<Commit> {
    val traverser = GitHistoryTraverserImpl(project, root, dataManager, dataGetter)

    filesHistoryCache.retainAll(files)
    filesHistoryCache.putAll(
      files
        .filter { it !in filesHistoryCache }
        .associateWith { file ->
          traverser.filterCommits(GitHistoryTraverser.TraverseCommitsFilter.File(file))
        }
    )

    return getCommitsData(traverser, files)
  }

  fun clear() {
    filesHistoryCache.clear()
  }
}