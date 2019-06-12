// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.UnorderedPair
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.VcsCachingHistory
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsFileRevisionEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogStructureFilter
import com.intellij.vcs.log.data.CompressedRefs
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.GraphCommit
import com.intellij.vcs.log.graph.GraphCommitImpl
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.history.FileHistoryVisiblePack.Companion.fileHistory
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
                      oldVisiblePack: VisiblePack,
                      sortType: PermanentGraph.SortType,
                      filters: VcsLogFilterCollection,
                      commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage> {
    val filePath = getFilePath(filters)
    if (filePath == null || filePath.isDirectory) {
      return vcsLogFilterer.filter(dataPack, oldVisiblePack, sortType, filters, commitCount)
    }
    val root = VcsLogUtil.getActualRoot(project, filePath)!!
    return MyWorker(root, filePath, getHash(filters)).filter(dataPack, oldVisiblePack, sortType, filters, commitCount)
  }

  override fun canFilterEmptyPack(filters: VcsLogFilterCollection): Boolean {
    return getFilePath(filters)?.run { !isDirectory } ?: false
  }

  private inner class MyWorker constructor(private val root: VirtualFile,
                                           private val filePath: FilePath,
                                           private val hash: Hash?) {

    fun filter(dataPack: DataPack,
               oldVisiblePack: VisiblePack,
               sortType: PermanentGraph.SortType,
               filters: VcsLogFilterCollection,
               commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage> {
      val start = System.currentTimeMillis()

      if (index.isIndexed(root) && dataPack.isFull) {
        val visiblePack = filterWithIndex(dataPack, oldVisiblePack, sortType, filters,
                                          commitCount == CommitCountStage.INITIAL)
        LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) + " for computing history for $filePath with index")
        if (checkNotEmpty(dataPack, visiblePack, true)) {
          return Pair(visiblePack, commitCount)
        }
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
            vcsLogFilterer.filter(dataPack, oldVisiblePack, sortType, filters, commitCount)
          }
        }
      }

      LOG.warn("Could not find vcs or history provider for file $filePath")
      return vcsLogFilterer.filter(dataPack, oldVisiblePack, sortType, filters, commitCount)
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
        val pathsMap = HashMap<Int, MaybeDeletedFilePath>()
        for (revision in revisions) {
          val revisionEx = revision as VcsFileRevisionEx
          pathsMap[getIndex(revision)] = MaybeDeletedFilePath(revisionEx.path, revisionEx.isDeleted)
        }
        val visibleGraph = vcsLogFilterer.createVisibleGraph(dataPack, sortType, null, pathsMap.keys)
        return FileHistoryVisiblePack(dataPack, visibleGraph, false, filters, pathsMap)
      }

      val commits = ArrayList<GraphCommit<Int>>(revisions.size)

      val pathsMap = HashMap<Int, MaybeDeletedFilePath>()
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
      return mapOf(Pair(root, compressedRefs))
    }

    private fun getIndex(revision: VcsFileRevision): Int {
      return storage.getCommitIndex(HashImpl.build(revision.revisionNumber.asString()), root)
    }

    private fun filterWithIndex(dataPack: DataPack,
                                oldVisiblePack: VisiblePack,
                                sortType: PermanentGraph.SortType,
                                filters: VcsLogFilterCollection,
                                isInitial: Boolean): VisiblePack {
      val oldFileHistory = oldVisiblePack.fileHistory
      if (isInitial) {
        return filterWithIndex(dataPack, filters, sortType, oldFileHistory.commitToRename,
                               FileHistory(emptyMap(), processedAdditionsDeletions = oldFileHistory.processedAdditionsDeletions))
      }
      val renames = collectRenamesFromProvider(oldFileHistory)
      return filterWithIndex(dataPack, filters, sortType, renames.union(oldFileHistory.commitToRename), oldFileHistory)
    }

    private fun filterWithIndex(dataPack: DataPack,
                                filters: VcsLogFilterCollection,
                                sortType: PermanentGraph.SortType,
                                oldRenames: MultiMap<UnorderedPair<Int>, Rename>,
                                oldFileHistory: FileHistory): VisiblePack {
      val matchingHeads = vcsLogFilterer.getMatchingHeads(dataPack.refsModel, setOf(root), filters)
      val data = indexDataGetter.createFileHistoryData(filePath).build(oldRenames)

      val permanentGraph = dataPack.permanentGraph
      if (permanentGraph !is PermanentGraphImpl) {
        val visibleGraph = vcsLogFilterer.createVisibleGraph(dataPack, sortType, matchingHeads, data.getCommits())
        return FileHistoryVisiblePack(dataPack, visibleGraph, false, filters, data.buildPathsMap())
      }

      if (matchingHeads.matchesNothing() || data.isEmpty) {
        return VisiblePack.EMPTY
      }

      val commit = (hash ?: getHead(dataPack))?.let { storage.getCommitIndex(it, root) }
      val historyBuilder = FileHistoryBuilder(commit, filePath, data, oldFileHistory)
      val visibleGraph = permanentGraph.createVisibleGraph(sortType, matchingHeads, data.getCommits(), historyBuilder)
      val fileHistory = historyBuilder.fileHistory

      return FileHistoryVisiblePack(dataPack, visibleGraph, fileHistory.unmatchedAdditionsDeletions.isNotEmpty(), filters, fileHistory)
    }

    private fun collectRenamesFromProvider(fileHistory: FileHistory): MultiMap<UnorderedPair<Int>, Rename> {
      if (fileHistory.unmatchedAdditionsDeletions.isEmpty()) return MultiMap.empty()

      val start = System.currentTimeMillis()

      val handler = logProviders[root]?.fileHistoryHandler ?: return MultiMap.empty()

      val renames = fileHistory.unmatchedAdditionsDeletions.mapNotNull {
        val parentHash = storage.getCommitId(it.parent)!!.hash
        val childHash = storage.getCommitId(it.child)!!.hash
        if (it.isAddition) handler.getRename(root, it.filePath, parentHash, childHash)
        else handler.getRename(root, it.filePath, childHash, parentHash)
      }.map { r ->
        Rename(r.filePath1, r.filePath2, storage.getCommitIndex(r.hash1, root), storage.getCommitIndex(r.hash2, root))
      }

      LOG.debug("Found ${renames.size} renames for ${fileHistory.unmatchedAdditionsDeletions.size} addition-deletions in " +
                StopWatch.formatTime(System.currentTimeMillis() - start))

      val result = MultiMap.createSmart<UnorderedPair<Int>, Rename>()
      renames.forEach { result.putValue(it.commits, it) }
      return result
    }

    private fun getHead(pack: DataPack): Hash? {
      return pack.refsModel.findBranch(VcsLogUtil.HEAD, root)?.commitHash
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
        else -> VcsLogFilterObject.fromBranch(VcsLogUtil.HEAD)
      }
      return VcsLogFilterObject.collection(fileFilter, revisionFilter)
    }
  }
}

private fun <K : Any?, V : Any?> MultiMap<K, V>.union(map: MultiMap<K, V>): MultiMap<K, V> {
  if (isEmpty) return map
  if (map.isEmpty) return this
  return MultiMap.createSmart<K, V>().also {
    it.putAllValues(this)
    it.putAllValues(map)
  }
}
