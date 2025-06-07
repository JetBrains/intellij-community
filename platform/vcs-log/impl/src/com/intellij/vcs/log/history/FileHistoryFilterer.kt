// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.vcs.log.history

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.UnorderedPair
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsFileRevisionEx
import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan.LogHistory
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpanAttribute
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.*
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.graph.GraphCommitImpl
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.graph.VisibleGraph
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.history.FileHistoryPaths.fileHistory
import com.intellij.vcs.log.history.FileHistoryPaths.withFileHistory
import com.intellij.vcs.log.statistics.VcsLogRepoSizeCollector
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil
import com.intellij.vcs.log.util.*
import com.intellij.vcs.log.visible.*
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcs.log.visible.filters.without
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.jetbrains.annotations.ApiStatus.Internal

internal class FileHistoryFilterer(private val logData: VcsLogData, private val logId: String) : VcsLogFilterer, Disposable {
  private val project = logData.project
  private val logProviders = logData.logProviders
  private val storage = logData.storage
  private val index = logData.index
  private val vcsLogFilterer = VcsLogFiltererImpl(logProviders, storage, logData.topCommitsCache, logData.fullCommitDetailsCache, index)

  private var fileHistoryTask: RevisionCollectorTask<CommitMetadataWithPath>? = null

  private val vcsLogObjectsFactory: VcsLogObjectsFactory get() = project.service()

  override val initialCommitCount: CommitCountStage get() = CommitCountStage(30, Int.MAX_VALUE)

  override fun filter(dataPack: DataPack,
                      oldVisiblePack: VisiblePack,
                      graphOptions: PermanentGraph.Options,
                      filters: VcsLogFilterCollection,
                      commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage> {
    val filePath = getFilePath(filters)
    val root = filePath?.let { VcsLogUtil.getActualRoot(project, filePath) }
    val hash = getHash(filters)
    val logProvider = logProviders[root]
    val fileHistoryHandler = logProvider?.getFileHistoryHandler(project)
    if (root != null && !filePath.isDirectory && fileHistoryHandler != null) {
      return MyWorker(logProvider.supportedVcs, fileHistoryHandler, root, filePath, hash).filter(dataPack, oldVisiblePack, graphOptions,
                                                                                                 filters, commitCount)
    }
    return vcsLogFilterer.filter(dataPack, oldVisiblePack, graphOptions, filters, commitCount)
  }

  override fun canFilterEmptyPack(filters: VcsLogFilterCollection): Boolean = true

  private fun cancelLastTask(wait: Boolean) {
    fileHistoryTask?.cancel(wait)
    fileHistoryTask = null
  }

  private fun createFileHistoryTask(historyHandler: VcsLogFileHistoryHandler,
                                    root: VirtualFile,
                                    filePath: FilePath,
                                    hash: Hash?,
                                    filters: VcsLogFilterCollection,
                                    commitCount: CommitCountStage): RevisionCollectorTask<CommitMetadataWithPath> {
    val oldHistoryTask = fileHistoryTask
    val oldCollector = oldHistoryTask?.collector
    if (oldHistoryTask != null && !oldHistoryTask.isCancelled && oldCollector is FileHistoryCollector &&
        oldCollector.filePath == filePath &&
        oldCollector.hash == hash &&
        oldCollector.filters == filters) {
      return oldHistoryTask
    }

    cancelLastTask(false)

    val collector = FileHistoryCollector(historyHandler, storage, vcsLogObjectsFactory, root, filePath, hash, filters, commitCount)
    val newHistoryTask = RevisionCollectorTask(project,
                                               collector,
                                               createProgressIndicator(),
                                               if (historyHandler.isFastStartSupported) createProgressIndicator() else null)
    fileHistoryTask = newHistoryTask
    return newHistoryTask
  }

  private fun createProgressIndicator(): ProgressIndicator {
    return logData.progress.createProgressIndicator(VcsLogProgress.ProgressKey("file history task for $logId"))
  }

  override fun dispose() {
    cancelLastTask(true)
  }

  private inner class MyWorker(private val vcsKey: VcsKey,
                               private val fileHistoryHandler: VcsLogFileHistoryHandler,
                               private val root: VirtualFile,
                               private val filePath: FilePath,
                               private val hash: Hash?) {

    fun filter(dataPack: DataPack,
               oldVisiblePack: VisiblePack,
               graphOptions: PermanentGraph.Options,
               filters: VcsLogFilterCollection,
               commitCount: CommitCountStage): Pair<VisiblePack, CommitCountStage> {
      val start = System.currentTimeMillis()
      TelemetryManager.getInstance().getTracer(VcsScope).spanBuilder(LogHistory.Computing.getName()).use { scope ->
        scope.setAttribute("filePath", filePath.toString())
        scope.setAttribute(VcsTelemetrySpanAttribute.FILE_HISTORY_IS_INITIAL.key, commitCount.isInitial)
        scope.setAttribute(VcsTelemetrySpanAttribute.VCS_NAME.key, VcsLogRepoSizeCollector.getVcsKeySafe(vcsKey))

        if (canFilterWithIndex(index, root, dataPack)) {
          cancelLastTask(false)
          val visiblePack = filterWithIndex(index.dataGetter!!, dataPack, oldVisiblePack, graphOptions, filters)

          LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) + " for computing history for $filePath with index")
          scope.setAttribute(VcsTelemetrySpanAttribute.FILE_HISTORY_TYPE.key, "index")
          scope.setAttribute("commitCount", visiblePack.visibleGraph.visibleCommitCount.toString())

          if (checkNotEmpty(dataPack, visiblePack, true)) {
            return Pair(visiblePack, commitCount)
          }
        }

        try {
          val visiblePack = filterWithVcs(dataPack, graphOptions, filters, commitCount)

          scope.setAttribute(VcsTelemetrySpanAttribute.FILE_HISTORY_TYPE.key, "history provider")
          scope.setAttribute("commitCount", visiblePack.visibleGraph.visibleCommitCount.toString())
          LOG.debug(StopWatch.formatTime(System.currentTimeMillis() - start) +
                    " for computing history for $filePath with history handler ${fileHistoryHandler.javaClass.name}")

          checkNotEmpty(dataPack, visiblePack, false)
          return@filter Pair(visiblePack, commitCount)
        }
        catch (e: VcsException) {
          LOG.error(e)
          scope.recordError(e)
          return Pair(VisiblePack.ErrorVisiblePack(dataPack, filters, e), commitCount)
        }
        catch (e: UnsupportedHistoryFiltersException) {
          LOG.error(e)
          scope.recordError(e)
          return Pair(VisiblePack.ErrorVisiblePack(dataPack, filters, e), commitCount)
        }
      }
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

    @Throws(VcsException::class, UnsupportedHistoryFiltersException::class)
    private fun filterWithVcs(dataPack: DataPack,
                              graphOptions: PermanentGraph.Options,
                              allFilters: VcsLogFilterCollection,
                              commitCount: CommitCountStage): VisiblePack {
      val filters = allFilters.without(VcsLogFileHistoryFilter::class.java)

      val (revisions, isDone) = createFileHistoryTask(fileHistoryHandler, root, filePath, hash, filters, commitCount).waitForRevisions(100)
      if (revisions.isEmpty()) return VisiblePack.EMPTY

      val isFastStart = commitCount.isInitial && fileHistoryHandler.isFastStartSupported
      if (dataPack.isFull && !isFastStart) {
        val pathMap = revisions.associate { Pair(it.commit, it.path) }
        val visibleGraph = createVisibleGraph(dataPack, graphOptions, null, pathMap.keys)
        return VisiblePack(dataPack, visibleGraph, !isDone, filters)
          .withFileHistory(FileHistory(pathMap))
          .apply {
            putUserData(FileHistorySpeedSearch.COMMIT_METADATA, toCommitMetadata(revisions))
          }
      }

      val commits = revisions.map { GraphCommitImpl.createCommit(it.commit, emptyList(), it.metadata.timestamp) }
      val refs = getFilteredRefs(dataPack)

      val fakeDataPack = DataPack.build(commits, refs, mapOf(root to logProviders[root]!!), storage, false)
      val visibleGraph = createVisibleGraph(fakeDataPack, graphOptions, null,
                                            null/*no need to filter here, since we do not have any extra commits in this pack*/)
      return VisiblePack(fakeDataPack, visibleGraph, !isDone, allFilters)
        .withFileHistory(FileHistory(revisions.associate { Pair(it.commit, it.path) }))
        .apply {
          putUserData(VisiblePack.NO_GRAPH_INFORMATION, true)
          putUserData(FileHistorySpeedSearch.COMMIT_METADATA, toCommitMetadata(revisions))
        }
    }

    private fun toCommitMetadata(revisions: List<CommitMetadataWithPath>): Int2ObjectMap<VcsCommitMetadata> {
      if (revisions.isEmpty()) {
        return Int2ObjectMaps.emptyMap()
      }

      val result = Int2ObjectOpenHashMap<VcsCommitMetadata>(revisions.size)
      for (revision in revisions) {
        result.put(revision.commit, revision.metadata)
      }
      return result
    }

    private fun getFilteredRefs(dataPack: DataPack): Map<VirtualFile, CompressedRefs> {
      val compressedRefs = dataPack.refsModel.allRefsByRoot[root] ?: CompressedRefs(emptySet(), storage)
      return mapOf(Pair(root, compressedRefs))
    }

    private fun filterWithIndex(indexDataGetter: IndexDataGetter,
                                dataPack: DataPack,
                                oldVisiblePack: VisiblePack,
                                graphOptions: PermanentGraph.Options,
                                filters: VcsLogFilterCollection): VisiblePack {
      val oldFileHistory = oldVisiblePack.fileHistory
      if (oldVisiblePack.filters != filters) {
        return filterWithIndex(indexDataGetter, dataPack, filters, graphOptions,
                               oldFileHistory.commitToRename,
                               FileHistory(emptyMap(), processedAdditionsDeletions = oldFileHistory.processedAdditionsDeletions))
      }
      val renames = collectRenamesFromProvider(oldFileHistory)
      return filterWithIndex(indexDataGetter, dataPack, filters, graphOptions, renames.union(oldFileHistory.commitToRename), oldFileHistory)
    }

    private fun filterWithIndex(indexDataGetter: IndexDataGetter,
                                dataPack: DataPack,
                                filters: VcsLogFilterCollection,
                                graphOptions: PermanentGraph.Options,
                                oldRenames: MultiMap<UnorderedPair<VcsLogCommitStorageIndex>, Rename>,
                                oldFileHistory: FileHistory): VisiblePack {
      val matchingHeads = vcsLogFilterer.getMatchingHeads(dataPack.refsModel, setOf(root), filters)
      val data = indexDataGetter.createFileHistoryData(filePath).build(oldRenames)

      val permanentGraph = dataPack.permanentGraph
      if (permanentGraph !is PermanentGraphImpl) {
        val visibleGraph = createVisibleGraph(dataPack, graphOptions, matchingHeads, data.getCommits())
        val fileHistory = FileHistory(data.buildFileStatesMap())
        return VisiblePack(dataPack, visibleGraph, false, filters).withFileHistory(fileHistory)
      }

      if (matchingHeads.matchesNothing() || data.isEmpty) {
        return VisiblePack.EMPTY
      }

      val commit = (hash ?: getHead(dataPack))?.let { storage.getCommitIndex(it, root) }
      val historyBuilder = FileHistoryBuilder(commit, filePath, data, oldFileHistory,
                                              removeTrivialMerges = FileHistoryBuilder.isRemoveTrivialMerges,
                                              refine = FileHistoryBuilder.isRefine)
      val visibleGraph = permanentGraph.createVisibleGraph(graphOptions, matchingHeads, data.getCommits(), historyBuilder)
      val fileHistory = historyBuilder.fileHistory

      return VisiblePack(dataPack, visibleGraph, fileHistory.unmatchedAdditionsDeletions.isNotEmpty(), filters).withFileHistory(fileHistory)
    }

    private fun collectRenamesFromProvider(fileHistory: FileHistory): MultiMap<UnorderedPair<VcsLogCommitStorageIndex>, Rename> {
      if (fileHistory.unmatchedAdditionsDeletions.isEmpty()) return MultiMap.empty()

      TelemetryManager.getInstance().getTracer(VcsScope).spanBuilder(LogHistory.CollectingRenames.getName()).use { span ->
        val renames = fileHistory.unmatchedAdditionsDeletions.mapNotNull { ad ->
          val parentHash = storage.getCommitId(ad.parent)!!.hash
          val childHash = storage.getCommitId(ad.child)!!.hash
          if (ad.isAddition) fileHistoryHandler.getRename(root, ad.filePath, parentHash, childHash)
          else fileHistoryHandler.getRename(root, ad.filePath, childHash, parentHash)
        }.map { r ->
          Rename(r.filePath1, r.filePath2, storage.getCommitIndex(r.hash1, root), storage.getCommitIndex(r.hash2, root))
        }

        span.setAttribute("renamesSize", renames.size.toLong())
        span.setAttribute("numberOfAdditionDeletions", fileHistory.unmatchedAdditionsDeletions.size.toLong())
        span.setAttribute(VcsTelemetrySpanAttribute.VCS_NAME.key, VcsLogRepoSizeCollector.getVcsKeySafe(vcsKey))

        val result = MultiMap<UnorderedPair<VcsLogCommitStorageIndex>, Rename>()
        renames.forEach { rename -> result.putValue(rename.commits, rename) }
        return result
      }
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

    private fun getStructureFilter(filters: VcsLogFilterCollection) = filters.detailsFilters.singleOrNull() as? VcsLogStructureFilter

    fun getFilePath(filters: VcsLogFilterCollection): FilePath? {
      val filter = getStructureFilter(filters) ?: return null
      return filter.files.singleOrNull()
    }

    @JvmStatic
    fun createFilters(path: FilePath, revision: Hash?, root: VirtualFile): VcsLogFilterCollection {
      val fileFilter = VcsLogFileHistoryFilter(path, revision)
      val revisionFilter = revision?.let { VcsLogFilterObject.fromCommit(CommitId(it, root)) }
                           ?: VcsLogFilterObject.fromBranch(VcsLogUtil.HEAD)
      return VcsLogFilterObject.collection(fileFilter, revisionFilter)
    }

    private fun createVisibleGraph(dataPack: DataPack,
                                   graphOptions: PermanentGraph.Options,
                                   matchingHeads: Set<VcsLogCommitStorageIndex>?,
                                   matchingCommits: Set<VcsLogCommitStorageIndex>?): VisibleGraph<VcsLogCommitStorageIndex> {
      if (matchingHeads.matchesNothing() || matchingCommits.matchesNothing()) {
        return EmptyVisibleGraph.getInstance()
      }
      return dataPack.permanentGraph.createVisibleGraph(graphOptions, matchingHeads, matchingCommits)
    }

    internal fun canFilterWithIndex(index: VcsLogIndex, root: VirtualFile, dataPack: DataPackBase): Boolean {
      return index.dataGetter != null && index.isIndexed(root) && dataPack.isFull && Registry.`is`("vcs.history.use.index")
    }
  }
}

private fun <K : Any, V : Any?> MultiMap<K, V>.union(map: MultiMap<K, V>): MultiMap<K, V> {
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

private data class CommitMetadataWithPath(@JvmField val commit: VcsLogCommitStorageIndex, @JvmField val metadata: VcsCommitMetadata, @JvmField val path: CommitFileState)

private class FileHistoryCollector(val handler: VcsLogFileHistoryHandler,
                                   val storage: VcsLogStorage,
                                   val factory: VcsLogObjectsFactory,
                                   val root: VirtualFile,
                                   val filePath: FilePath,
                                   val hash: Hash?,
                                   val filters: VcsLogFilterCollection,
                                   val commitCount: CommitCountStage) : RevisionCollector<CommitMetadataWithPath> {

  @Throws(VcsException::class)
  override fun collectRevisions(consumer: (CommitMetadataWithPath) -> Unit) {
    TelemetryManager.getInstance().getTracer(VcsScope).spanBuilder(LogHistory.CollectingRevisionsFromHandler.getName()).use { span ->
      span.setAttribute(VcsTelemetrySpanAttribute.VCS_NAME.key, VcsLogRepoSizeCollector.getVcsKeySafe(handler.supportedVcs))
      span.setAttribute("handlerClass", handler.javaClass.name)

      var revisionsCount = 0
      handler.collectHistory(root, filePath, hash, filters) { revision ->
        consumer(createCommitMetadataWithPath(revision))
        revisionsCount++
      }

      span.setAttribute("commitCount", revisionsCount.toString())
    }
  }

  override fun collectRevisionsFast(consumer: (CommitMetadataWithPath) -> Unit) {
    handler.getHistoryFast(root, filePath, hash, filters, commitCount.count) { revision ->
      consumer(createCommitMetadataWithPath(revision))
    }
  }

  private fun createCommitMetadataWithPath(revision: VcsFileRevision): CommitMetadataWithPath {
    return factory.createCommitMetadataWithPath(storage, revision as VcsFileRevisionEx, root)
  }
}

private fun VcsLogObjectsFactory.createCommitMetadataWithPath(storage: VcsLogStorage, revision: VcsFileRevisionEx,
                                                              root: VirtualFile): CommitMetadataWithPath {
  val commitHash = createHash(revision.revisionNumber.asString())
  val metadata = createCommitMetadata(commitHash, emptyList(), revision.revisionDate.time, root,
                                      CommitPresentationUtil.getSubject(revision.commitMessage!!),
                                      revision.author!!, revision.authorEmail!!,
                                      revision.commitMessage!!,
                                      revision.committerName!!, revision.committerEmail!!, revision.authorDate!!.time)
  return CommitMetadataWithPath(storage.getCommitIndex(commitHash, root), metadata,
                                CommitFileState(revision.path, revision.isDeleted))
}
