// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.VcsCachingHistory
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsFileRevisionEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.CompressedRefs
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.VcsLogModifiableIndex
import com.intellij.vcs.log.graph.GraphCommit
import com.intellij.vcs.log.graph.GraphCommitImpl
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.graph.VisibleGraph
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.StopWatch
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.util.findBranch
import com.intellij.vcs.log.visible.*
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject

internal class FileHistoryFilterer(logData: VcsLogData) : VcsLogFilterer {
  private val project = logData.project
  private val logProviders = logData.logProviders
  private val storage = logData.storage
  private val index = logData.index
  private val indexDataGetter = index.dataGetter!!
  private val vcsLogFilterer = VcsLogFiltererImpl(logProviders, storage, logData.topCommitsCache,
                                                  logData.commitDetailsGetter, index)

  override fun filter(dataPack: DataPack,
                      sortType: PermanentGraph.SortType,
                      filters: VcsLogFilterCollection,
                      commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage> {
    val filePath = getFilePath(filters)
    if (filePath == null || (filePath.isDirectory && logProviders.keys.contains(filePath.virtualFile))) {
      return vcsLogFilterer.filter(dataPack, sortType, filters, commitCount)
    }
    val root = VcsLogUtil.getActualRoot(project, filePath)!!
    return MyWorker(root, filePath, getHash(filters)).filter(dataPack, sortType, filters, commitCount)
  }

  override fun canFilterEmptyPack(filters: VcsLogFilterCollection): Boolean {
    return getFilePath(filters)?.run { !isDirectory } ?: false
  }

  private inner class MyWorker constructor(private val root: VirtualFile,
                                           private val filePath: FilePath,
                                           private val hash: Hash?) {

    fun filter(dataPack: DataPack,
               sortType: PermanentGraph.SortType,
               filters: VcsLogFilterCollection,
               commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage> {
      val start = System.currentTimeMillis()

      if (index.isIndexed(root) && (dataPack.isFull || filePath.isDirectory)) {
        val visiblePack = filterWithIndex(dataPack, sortType, filters)
        LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) + " for computing history for $filePath with index")
        if (checkNotEmpty(dataPack, visiblePack, true)) {
          return Pair(visiblePack, commitCount)
        }
      }

      if (filePath.isDirectory) {
        return vcsLogFilterer.filter(dataPack, sortType, filters, commitCount)
      }

      ProjectLevelVcsManager.getInstance(project).getVcsFor(root)?.let { vcs ->
        if (vcs.vcsHistoryProvider != null) {
          return@filter try {
            val visiblePack = filterWithProvider(vcs, dataPack, sortType, filters)
            LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) +
                      " for computing history for $filePath with history provider")
            checkNotEmpty(dataPack, visiblePack, false)
            Pair(visiblePack, commitCount)
          }
          catch (e: VcsException) {
            LOG.error(e)
            vcsLogFilterer.filter(dataPack, sortType, filters, commitCount)
          }
        }
      }

      LOG.warn("Could not find vcs or history provider for file $filePath")
      return vcsLogFilterer.filter(dataPack, sortType, filters, commitCount)
    }

    private fun checkNotEmpty(dataPack: DataPack, visiblePack: VisiblePack, withIndex: Boolean): Boolean {
      if (!dataPack.isFull) {
        LOG.debug("Data pack is not full while computing file history for $filePath\n" +
                  "Found ${visiblePack.visibleGraph.visibleCommitCount} commits")
        return true
      }
      else if (visiblePack.visibleGraph.visibleCommitCount == 0) {
        LOG.warn("Empty file history from ${if (withIndex) "index" else "provider"} for $filePath")
        return false
      }
      return true
    }

    @Throws(VcsException::class)
    private fun filterWithProvider(vcs: AbstractVcs<*>,
                                   dataPack: DataPack,
                                   sortType: PermanentGraph.SortType,
                                   filters: VcsLogFilterCollection): VisiblePack {
      val revisionNumber = if (hash != null) VcsLogUtil.convertToRevisionNumber(hash) else null
      val revisions = VcsCachingHistory.collect(vcs, filePath, revisionNumber)

      if (revisions.isEmpty()) return VisiblePack.EMPTY

      if (dataPack.isFull) {
        val pathsMap = ContainerUtil.newHashMap<Int, MaybeDeletedFilePath>()
        for (revision in revisions) {
          val revisionEx = revision as VcsFileRevisionEx
          pathsMap[getIndex(revision)] = MaybeDeletedFilePath(revisionEx.path, revisionEx.isDeleted)
        }
        val visibleGraph = vcsLogFilterer.createVisibleGraph(dataPack, sortType, null, pathsMap.keys)
        return FileHistoryVisiblePack(dataPack, visibleGraph, false, filters, pathsMap)
      }

      val commits = ContainerUtil.newArrayListWithCapacity<GraphCommit<Int>>(revisions.size)

      val pathsMap = ContainerUtil.newHashMap<Int, MaybeDeletedFilePath>()
      for (revision in revisions) {
        val index = getIndex(revision)
        val revisionEx = revision as VcsFileRevisionEx
        pathsMap[index] = MaybeDeletedFilePath(revisionEx.path, revisionEx.isDeleted)
        commits.add(GraphCommitImpl.createCommit(index, emptyList(), revision.getRevisionDate().time))
      }

      val refs = getFilteredRefs(dataPack)

      val fakeDataPack = DataPack.build(commits, refs, mapOf(root to logProviders[root]), storage, false)
      val visibleGraph = vcsLogFilterer.createVisibleGraph(fakeDataPack, sortType, null,
                                                           null/*no need to filter here, since we do not have any extra commits in this pack*/)
      return FileHistoryVisiblePack(fakeDataPack, visibleGraph, false, filters, pathsMap)
    }

    private fun getFilteredRefs(dataPack: DataPack): Map<VirtualFile, CompressedRefs> {
      val compressedRefs = dataPack.refsModel.allRefsByRoot[root] ?: CompressedRefs(emptySet(), storage)
      return mapOf(kotlin.Pair(root, compressedRefs))
    }

    private fun getIndex(revision: VcsFileRevision): Int {
      return storage.getCommitIndex(HashImpl.build(revision.revisionNumber.asString()), root)
    }

    private fun filterWithIndex(dataPack: DataPack,
                                sortType: PermanentGraph.SortType,
                                filters: VcsLogFilterCollection): VisiblePack {
      val matchingHeads = vcsLogFilterer.getMatchingHeads(dataPack.refsModel, setOf(root), filters)
      val data = indexDataGetter.createFileNamesData(filePath)

      val permanentGraph = dataPack.permanentGraph
      if (permanentGraph !is PermanentGraphImpl) {
        val visibleGraph = vcsLogFilterer.createVisibleGraph(dataPack, sortType, matchingHeads, data.getCommits())
        return FileHistoryVisiblePack(dataPack, visibleGraph, false, filters, data.buildPathsMap())
      }

      if (matchingHeads.matchesNothing() || data.isEmpty) {
        return VisiblePack.EMPTY
      }

      val commit = (hash ?: getHead(dataPack))?.let { storage.getCommitIndex(it, root) }
      val historyBuilder = FileHistoryBuilder(commit, filePath, data)
      val visibleGraph = permanentGraph.createVisibleGraph(sortType, matchingHeads, data.getCommits(), historyBuilder)

      if (!filePath.isDirectory) reindexFirstCommitsIfNeeded(visibleGraph)
      return FileHistoryVisiblePack(dataPack, visibleGraph, false, filters, historyBuilder.pathsMap)
    }

    private fun reindexFirstCommitsIfNeeded(graph: VisibleGraph<Int>) {
      // we may not have renames big commits, may need to reindex them
      if (graph is VisibleGraphImpl<*>) {
        val liteLinearGraph = LinearGraphUtils.asLiteLinearGraph((graph as VisibleGraphImpl<*>).linearGraph)
        for (row in 0 until liteLinearGraph.nodesCount()) {
          // checking if commit is a root commit (which means file was added or renamed there)
          if (liteLinearGraph.getNodes(row, LiteLinearGraph.NodeFilter.DOWN).isEmpty()
              && index is VcsLogModifiableIndex) {
            index.reindexWithRenames(graph.getRowInfo(row).commit, root)
          }
        }
      }
    }

    private fun getHead(pack: DataPack): Hash? {
      return pack.refsModel.findBranch("HEAD", root)?.commitHash
    }
  }

  private fun getStructureFilter(filters: VcsLogFilterCollection) = filters.detailsFilters.singleOrNull() as? VcsLogStructureFilter

  private fun getFilePath(filters: VcsLogFilterCollection): FilePath? {
    val filter = getStructureFilter(filters) ?: return null
    return filter.files.singleOrNull()
  }

  private fun getHash(filters: VcsLogFilterCollection): Hash? {
    val fileHistoryFilter = getStructureFilter(filters) as? VcsLogFileHistoryFilter
    if (fileHistoryFilter != null) {
      return fileHistoryFilter.hash
    }

    val revisionFilter = filters.get(VcsLogFilterCollection.REVISION_FILTER)
    return revisionFilter?.heads?.singleOrNull()?.hash
  }

  companion object {
    private val LOG = Logger.getInstance(FileHistoryFilterer::class.java)

    @JvmStatic
    fun createFilters(path: FilePath,
                      revision: Hash?,
                      root: VirtualFile,
                      showAllBranches: Boolean): VcsLogFilterCollection {
      val fileFilter = VcsLogFileHistoryFilter(path, revision)

      val revisionFilter = when {
        showAllBranches -> null
        revision != null -> VcsLogFilterObject.fromCommit(CommitId(revision, root))
        else -> VcsLogFilterObject.fromBranch("HEAD")
      }
      return VcsLogFilterObject.collection(fileFilter, revisionFilter)
    }
  }
}
