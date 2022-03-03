// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UnorderedPair
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.VcsCachingHistory
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsFileRevisionEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.CompressedRefs
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.VcsLogProgress
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.graph.GraphCommitImpl
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.graph.VisibleGraph
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.history.FileHistoryPaths.fileHistory
import com.intellij.vcs.log.history.FileHistoryPaths.withFileHistory
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil
import com.intellij.vcs.log.util.StopWatch
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.util.findBranch
import com.intellij.vcs.log.visible.*
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

class FileHistoryFilterer(private val logData: VcsLogData, private val logId: String) : VcsLogFilterer, Disposable {
  private val project = logData.project
  private val logProviders = logData.logProviders
  private val storage = logData.storage
  private val index = logData.index
  private val vcsLogFilterer = VcsLogFiltererImpl(logProviders, storage, logData.topCommitsCache, logData.commitDetailsGetter, index)

  private var fileHistoryTask: FileHistoryTask? = null

  override fun filter(dataPack: DataPack,
                      oldVisiblePack: VisiblePack,
                      sortType: PermanentGraph.SortType,
                      filters: VcsLogFilterCollection,
                      commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage> {
    val filePath = getFilePath(filters)
    val root = filePath?.let { VcsLogUtil.getActualRoot(project, filePath) }
    val hash = getHash(filters)
    if (root != null && !filePath.isDirectory) {
      val result = MyWorker(root, filePath, hash).filter(dataPack, oldVisiblePack, sortType, filters, commitCount)
      if (result != null) return result
    }
    return vcsLogFilterer.filter(dataPack, oldVisiblePack, sortType, filters, commitCount)
  }

  override fun canFilterEmptyPack(filters: VcsLogFilterCollection): Boolean = true

  private fun cancelLastTask(wait: Boolean) {
    fileHistoryTask?.cancel(wait)
    fileHistoryTask = null
  }

  private fun createFileHistoryTask(vcs: AbstractVcs, root: VirtualFile, filePath: FilePath, hash: Hash?, isInitial: Boolean): FileHistoryTask {
    val oldHistoryTask = fileHistoryTask
    if (oldHistoryTask != null && !oldHistoryTask.isCancelled && !isInitial &&
        oldHistoryTask.filePath == filePath && oldHistoryTask.hash == hash) return oldHistoryTask

    cancelLastTask(false)

    val factory = project.service<VcsLogObjectsFactory>()
    val newHistoryTask = object : FileHistoryTask(vcs, filePath, hash, createProgressIndicator()) {
      override fun createCommitMetadataWithPath(revision: VcsFileRevision): CommitMetadataWithPath {
        val revisionEx = revision as VcsFileRevisionEx
        val commitHash = factory.createHash(revisionEx.revisionNumber.asString())
        val metadata = factory.createCommitMetadata(commitHash, emptyList(), revisionEx.revisionDate.time, root,
                                                    CommitPresentationUtil.getSubject(revisionEx.commitMessage!!),
                                                    revisionEx.author!!, revisionEx.authorEmail!!,
                                                    revisionEx.commitMessage!!,
                                                    revisionEx.committerName!!, revisionEx.committerEmail!!, revisionEx.authorDate!!.time)
        return CommitMetadataWithPath(storage.getCommitIndex(commitHash, root), metadata,
                                      MaybeDeletedFilePath(revisionEx.path, revisionEx.isDeleted))
      }
    }
    fileHistoryTask = newHistoryTask
    return newHistoryTask
  }

  private fun createProgressIndicator(): ProgressIndicator {
    return logData.progress.createProgressIndicator(VcsLogProgress.ProgressKey("file history task for $logId"))
  }

  override fun dispose() {
    cancelLastTask(true)
  }

  private inner class MyWorker constructor(private val root: VirtualFile,
                                           private val filePath: FilePath,
                                           private val hash: Hash?) {

    fun filter(dataPack: DataPack,
               oldVisiblePack: VisiblePack,
               sortType: PermanentGraph.SortType,
               filters: VcsLogFilterCollection,
               commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage>? {
      val start = System.currentTimeMillis()
      val isInitial = commitCount == CommitCountStage.INITIAL

      val indexDataGetter = index.dataGetter
      if (indexDataGetter != null && index.isIndexed(root) && dataPack.isFull) {
        cancelLastTask(false)
        val visiblePack = filterWithIndex(indexDataGetter, dataPack, oldVisiblePack, sortType, filters, isInitial)
        LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) + " for computing history for $filePath with index")
        if (checkNotEmpty(dataPack, visiblePack, true)) {
          return Pair(visiblePack, commitCount)
        }
      }

      ProjectLevelVcsManager.getInstance(project).getVcsFor(root)?.let { vcs ->
        if (vcs.vcsHistoryProvider != null) {
          try {
            val visiblePack = filterWithProvider(vcs, dataPack, sortType, filters, isInitial)
            LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) +
                      " for computing history for $filePath with history provider")
            checkNotEmpty(dataPack, visiblePack, false)
            return@filter Pair(visiblePack, commitCount)
          }
          catch (e: VcsException) {
            LOG.error(e)
          }
        }
      }

      LOG.warn("Could not find vcs or history provider for file $filePath")
      return null
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
    private fun filterWithProvider(vcs: AbstractVcs,
                                   dataPack: DataPack,
                                   sortType: PermanentGraph.SortType,
                                   filters: VcsLogFilterCollection,
                                   isInitial: Boolean): VisiblePack {
      val (revisions, isDone) = createFileHistoryTask(vcs, root, filePath, hash, isInitial).waitForRevisions()

      if (revisions.isEmpty()) return VisiblePack.EMPTY

      if (dataPack.isFull) {
        val pathsMap = revisions.associate { Pair(it.commit, it.path) }
        val visibleGraph = createVisibleGraph(dataPack, sortType, null, pathsMap.keys)
        return VisiblePack(dataPack, visibleGraph, !isDone, filters)
          .withFileHistory(FileHistory(pathsMap))
          .apply {
            putUserData(FileHistorySpeedSearch.COMMIT_METADATA, revisions.associate { Pair(it.commit, it.metadata) })
          }
      }

      val commits = revisions.map { GraphCommitImpl.createCommit(it.commit, emptyList(), it.metadata.timestamp) }
      val refs = getFilteredRefs(dataPack)

      val fakeDataPack = DataPack.build(commits, refs, mapOf(root to logProviders[root]), storage, false)
      val visibleGraph = createVisibleGraph(fakeDataPack, sortType, null,
                                            null/*no need to filter here, since we do not have any extra commits in this pack*/)
      return VisiblePack(fakeDataPack, visibleGraph, !isDone, filters)
        .withFileHistory(FileHistory(revisions.associate { Pair(it.commit, it.path) }))
        .apply {
          putUserData(NO_PARENTS_INFO, true)
          putUserData(FileHistorySpeedSearch.COMMIT_METADATA, revisions.associate { Pair(it.commit, it.metadata) })
        }
    }

    private fun getFilteredRefs(dataPack: DataPack): Map<VirtualFile, CompressedRefs> {
      val compressedRefs = dataPack.refsModel.allRefsByRoot[root] ?: CompressedRefs(emptySet(), storage)
      return mapOf(Pair(root, compressedRefs))
    }

    private fun filterWithIndex(indexDataGetter: IndexDataGetter,
                                dataPack: DataPack,
                                oldVisiblePack: VisiblePack,
                                sortType: PermanentGraph.SortType,
                                filters: VcsLogFilterCollection,
                                isInitial: Boolean): VisiblePack {
      val oldFileHistory = oldVisiblePack.fileHistory
      if (isInitial) {
        return filterWithIndex(indexDataGetter, dataPack, filters, sortType,
                               oldFileHistory.commitToRename,
                               FileHistory(emptyMap(), processedAdditionsDeletions = oldFileHistory.processedAdditionsDeletions))
      }
      val renames = collectRenamesFromProvider(oldFileHistory)
      return filterWithIndex(indexDataGetter, dataPack, filters, sortType, renames.union(oldFileHistory.commitToRename), oldFileHistory)
    }

    private fun filterWithIndex(indexDataGetter: IndexDataGetter,
                                dataPack: DataPack,
                                filters: VcsLogFilterCollection,
                                sortType: PermanentGraph.SortType,
                                oldRenames: MultiMap<UnorderedPair<Int>, Rename>,
                                oldFileHistory: FileHistory): VisiblePack {
      val matchingHeads = vcsLogFilterer.getMatchingHeads(dataPack.refsModel, setOf(root), filters)
      val data = indexDataGetter.createFileHistoryData(filePath).build(oldRenames)

      val permanentGraph = dataPack.permanentGraph
      if (permanentGraph !is PermanentGraphImpl) {
        val visibleGraph = createVisibleGraph(dataPack, sortType, matchingHeads, data.getCommits())
        val fileHistory = FileHistory(data.buildPathsMap())
        return VisiblePack(dataPack, visibleGraph, false, filters).withFileHistory(fileHistory)
      }

      if (matchingHeads.matchesNothing() || data.isEmpty) {
        return VisiblePack.EMPTY
      }

      val commit = (hash ?: getHead(dataPack))?.let { storage.getCommitIndex(it, root) }
      val historyBuilder = FileHistoryBuilder(commit, filePath, data, oldFileHistory,
                                              removeTrivialMerges = FileHistoryBuilder.isRemoveTrivialMerges,
                                              refine = FileHistoryBuilder.isRefine)
      val visibleGraph = permanentGraph.createVisibleGraph(sortType, matchingHeads, data.getCommits(), historyBuilder)
      val fileHistory = historyBuilder.fileHistory

      return VisiblePack(dataPack, visibleGraph, fileHistory.unmatchedAdditionsDeletions.isNotEmpty(), filters).withFileHistory(fileHistory)
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

      val result = MultiMap<UnorderedPair<Int>, Rename>()
      renames.forEach { result.putValue(it.commits, it) }
      return result
    }

    private fun getHead(pack: DataPack): Hash? {
      return pack.refsModel.findBranch(VcsLogUtil.HEAD, root)?.commitHash
    }
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
    private val LOG = logger<FileHistoryFilterer>()

    @JvmField
    val NO_PARENTS_INFO = Key.create<Boolean>("NO_PARENTS_INFO")

    private fun getStructureFilter(filters: VcsLogFilterCollection) = filters.detailsFilters.singleOrNull() as? VcsLogStructureFilter

    fun getFilePath(filters: VcsLogFilterCollection): FilePath? {
      val filter = getStructureFilter(filters) ?: return null
      return filter.files.singleOrNull()
    }

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

    private fun createVisibleGraph(dataPack: DataPack,
                                   sortType: PermanentGraph.SortType,
                                   matchingHeads: Set<Int>?,
                                   matchingCommits: Set<Int>?): VisibleGraph<Int> {
      if (matchingHeads.matchesNothing() || matchingCommits.matchesNothing()) {
        return EmptyVisibleGraph.getInstance()
      }
      return dataPack.permanentGraph.createVisibleGraph(sortType, matchingHeads, matchingCommits)
    }
  }
}

private fun <K : Any?, V : Any?> MultiMap<K, V>.union(map: MultiMap<K, V>): MultiMap<K, V> {
  if (isEmpty) {
    return map
  }
  if (map.isEmpty) {
    return this
  }

  val result = MultiMap<K, V>()
  result.putAllValues(this)
  result.putAllValues(map)
  return result
}

private data class CommitMetadataWithPath(val commit: Int, val metadata: VcsCommitMetadata, val path: MaybeDeletedFilePath)

private abstract class FileHistoryTask(val vcs: AbstractVcs, val filePath: FilePath, val hash: Hash?, val indicator: ProgressIndicator) {
  private val revisionNumber = if (hash != null) VcsLogUtil.convertToRevisionNumber(hash) else null

  private val future: Future<*>
  private val _revisions = ConcurrentLinkedQueue<CommitMetadataWithPath>()
  private val exception = AtomicReference<VcsException>()

  @Volatile
  private var lastSize = 0

  val isCancelled get() = indicator.isCanceled

  init {
    future = AppExecutorUtil.getAppExecutorService().submit {
      ProgressManager.getInstance().runProcess(Runnable {
        try {
          VcsCachingHistory.collect(vcs, filePath, revisionNumber) { revision ->
            _revisions.add(createCommitMetadataWithPath(revision))
          }
        }
        catch (e: VcsException) {
          exception.set(e)
        }
      }, indicator)
    }
  }

  protected abstract fun createCommitMetadataWithPath(revision: VcsFileRevision): CommitMetadataWithPath

  @Throws(VcsException::class)
  fun waitForRevisions(): Pair<List<CommitMetadataWithPath>, Boolean> {
    throwOnError()
    while (_revisions.size == lastSize) {
      try {
        future.get(100, TimeUnit.MILLISECONDS)
        ProgressManager.checkCanceled()
        throwOnError()
        return Pair(getRevisionsSnapshot(), true)
      }
      catch (_: TimeoutException) {
      }
    }
    return Pair(getRevisionsSnapshot(), false)
  }

  private fun getRevisionsSnapshot(): List<CommitMetadataWithPath> {
    val list = _revisions.toList()
    lastSize = list.size
    return list
  }

  @Throws(VcsException::class)
  private fun throwOnError() {
    if (exception.get() != null) throw VcsException(exception.get())
  }

  fun cancel(wait: Boolean) {
    indicator.cancel()
    if (wait) {
      try {
        future.get(20, TimeUnit.MILLISECONDS)
      }
      catch (_: Throwable) {
      }
    }
  }
}
