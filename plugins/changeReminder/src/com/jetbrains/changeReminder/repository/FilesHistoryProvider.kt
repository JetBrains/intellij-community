// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.repository

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.graph.utils.DfsWalk
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.jetbrains.changeReminder.processCommitsFromHashes
import com.jetbrains.changeReminder.retainAll
import gnu.trove.TIntHashSet

data class Commit(val id: Int, val time: Long, val author: String, val files: Set<FilePath>)

internal class FilesHistoryProvider(private val project: Project,
                                    private val dataManager: VcsLogData,
                                    private val dataGetter: IndexDataGetter) {
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
      val affectedPaths = commit.affectedPaths
      if (affectedPaths.isNotEmpty()) {
        val id = hashes[commit.id.asString()] ?: return@consume
        val time = commit.commitTime
        val author = commit.author
        commitsData.add(Commit(id, time, author.name, affectedPaths))
      }
    }
    return commitsData
  }

  private fun getCommitsFromHead(root: VirtualFile): TIntHashSet {
    val dataPack = dataManager.dataPack
    val branchName = dataManager.getLogProvider(root).getCurrentBranch(root) ?: return TIntHashSet()
    val branchRef = VcsLogUtil.findBranch(dataPack.refsModel, root, branchName) ?: return TIntHashSet()
    val branchIndex = dataManager.getCommitIndex(branchRef.commitHash, branchRef.root)

    val permanentGraph = dataPack.permanentGraph as? PermanentGraphImpl<Int> ?: return TIntHashSet()

    val branchNodeId = permanentGraph.permanentCommitsInfo.getNodeId(branchIndex)
    val commitsFromHead = TIntHashSet()
    DfsWalk(listOf(branchNodeId), permanentGraph.linearGraph).walk(true) {
      ProgressManager.checkCanceled()
      commitsFromHead.add(permanentGraph.permanentCommitsInfo.getCommitId(it))
      true
    }
    return commitsFromHead
  }

  fun getFilesHistory(root: VirtualFile, files: Collection<FilePath>): Collection<Commit> {
    filesHistoryCache.retainAll(files)
    val commitsFromHead = getCommitsFromHead(root)
    filesHistoryCache.putAll(files
                               .filter { it !in filesHistoryCache }
                               .associateWith { file ->
                                 getCommitHashesWithFile(file).filter { it in commitsFromHead }
                               })
    val commits = files.mapNotNull { filesHistoryCache[it] }.flatten()

    return getCommitsData(root, commits)
  }

  fun clear() {
    filesHistoryCache.clear()
  }
}