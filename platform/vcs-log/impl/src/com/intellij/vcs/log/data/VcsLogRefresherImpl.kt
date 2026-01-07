// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan.LogData.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.vcs.log.*
import com.intellij.vcs.log.VcsLogProvider.RefsLoadingPolicy
import com.intellij.vcs.log.data.util.trace
import com.intellij.vcs.log.graph.GraphCommit
import com.intellij.vcs.log.graph.GraphCommitImpl
import com.intellij.vcs.log.impl.RequirementsImpl
import com.intellij.vcs.log.impl.SimpleLogProviderRequirements
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.jetbrains.annotations.TestOnly
import java.util.function.Consumer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

private val LOG = Logger.getInstance(VcsLogRefresherImpl::class.java)

internal class VcsLogRefresherImpl(
  parentCs: CoroutineScope,
  private val storage: VcsLogStorage,
  private val providers: Map<VirtualFile, VcsLogProvider>,
  private val progress: VcsLogProgress,
  private val commitDataConsumer: VcsLogCommitDataConsumer?,
  private val dataPackUpdateHandler: Consumer<VcsLogGraphData>,
  private val recentCommitCount: Int,
) : VcsLogRefresher {
  private val refreshRequests = Channel<RefreshRequest>(Channel.UNLIMITED)
  private val refresherJob: Job

  @Volatile
  override var currentDataPack: VcsLogGraphData = VcsLogGraphData.Empty
    private set(value) {
      field = value
      LOG.debug("New data pack received: $value")
      dataPackUpdateHandler.accept(value)
    }

  private val _isBusy = MutableStateFlow(false)
  val isBusy = _isBusy.asStateFlow()

  private val tracer = TelemetryManager.getInstance().getTracer(VcsScope)

  init {
    refresherJob = parentCs.launch(Dispatchers.Default + CoroutineName("Vcs Log Refresher"), CoroutineStart.LAZY) {
      try {
        _isBusy.value = true
        currentDataPack = runInitialRefresh()
        refreshRequests.send(RefreshRequest(providers.keys, false))
        handleRefreshRequests()
      }
      finally {
        shutDown()
      }
    }
  }

  private suspend fun runInitialRefresh(): VcsLogGraphData {
    return try {
      loadFirstBlock()
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      LOG.info("Failed to load initial data", e)
      VcsLogGraphData.Error(e)
    }
  }

  /**
   * Continuously handles incoming refresh requests for updating the VCS log data pack.
   * The method runs indefinitely, unless [refresherJob] is interrupted.
   *
   * If a series of refresh requests is received, they are accumulated and processed in a batch.
   * Moreover, if a new request is received while processing a batch, no data pack update is performed until
   * it is processed.
   */
  private suspend fun handleRefreshRequests(): Nothing {
    while (true) {
      checkCanceled()
      val request = checkWasRequested() ?: refreshRequests.receive()
      val requests = mutableListOf(request)
      runCatching {
        progress.runWithProgress(VcsLogData.DATA_PACK_REFRESH) {
          var dataPack = currentDataPack
          val refreshSessionData = RefreshSessionData()
          while (true) {
            checkCanceled()
            val cumulativeRequest = accumulateRequests(requests) ?: break
            LOG.debug("Cumulative refresh request: $cumulativeRequest")
            dataPack = handleRequest(dataPack, refreshSessionData, cumulativeRequest)
          }
          if (dataPack !== currentDataPack) {
            currentDataPack = dataPack
          }
        }
      }.getOrHandleException { e ->
        LOG.warn("Failed to handle the VCS Log refresh requests", e)
      }
    }
  }

  private fun accumulateRequests(accumulator: MutableList<RefreshRequest>): RefreshRequest? {
    refreshRequests.receiveAll(accumulator::add)
    if (accumulator.isEmpty()) {
      LOG.trace("No refresh requests received")
      return null
    }

    LOG.debug("Refresh requests: $accumulator")
    val cumulativeRequest = RefreshRequest.fold(accumulator)
    accumulator.clear()
    return cumulativeRequest
  }

  private suspend fun handleRequest(
    currentDataPack: VcsLogGraphData,
    refreshSessionData: RefreshSessionData,
    request: RefreshRequest,
  ): VcsLogGraphData {
    val rootsToRefresh = request.roots
    if (rootsToRefresh.isEmpty()) {
      LOG.debug("No roots to refresh")
      return currentDataPack
    }

    LOG.debug("Refreshing roots: ${request.roots}")
    if (request.loadOverlayData && VcsLogGraphData.OverlayData.isEnabled) {
      val smallDataPack = loadSmallDataPack()
      if (smallDataPack !== VcsLogGraphData.Empty) {
        dataPackUpdateHandler.accept(smallDataPack)
      }
    }

    checkCanceled()
    return runCatching {
      val partiallyLoadedLog =
        if (currentDataPack.isFull) loadUpdatedDataPack(currentDataPack, refreshSessionData, rootsToRefresh)
        else null

      partiallyLoadedLog ?: loadFullLog()
    }.recoverCatching { e ->
      rethrowControlFlowException(e)
      VcsLogGraphData.Error(e)
    }.getOrThrow()
  }

  override fun initialize() {
    refresherJob.start()
  }

  /**
   * Synchronously checks if a request was submitted and resets the busy state if there are none.
   *
   * @return true if a request was submitted, false if not.
   */
  @Synchronized
  private fun checkWasRequested(): RefreshRequest? {
    val request = refreshRequests.tryReceive().getOrNull()
    if (request == null) {
      _isBusy.value = false
    }
    return request
  }

  @Synchronized
  override fun refresh(rootsToRefresh: Collection<VirtualFile>, optimized: Boolean) {
    if (rootsToRefresh.isEmpty()) return

    refresherJob.start()
    refreshRequests.trySend(RefreshRequest(rootsToRefresh.toSet(), optimized)).onSuccess {
      _isBusy.value = true
    }.onClosed {
      LOG.warn("Log refresher is already shut down. Refresh will not be performed", it)
    }.onFailure {
      LOG.error("Failed to send a VCS log refresh request", it)
    }
  }

  @Synchronized
  private fun shutDown() {
    refreshRequests.close()
    _isBusy.value = false
  }

  @TestOnly
  suspend fun awaitNotBusy() {
    _isBusy.first { !it }
  }

  private suspend fun loadFirstBlock(): VcsLogGraphData =
    tracer.trace(Initializing) {
      LOG.debug("Loading the first block")
      val commitCountRequirements = SimpleLogProviderRequirements(recentCommitCount)
      val (data, loadTime) = measureTimedValue { loadRecentData(providers.keys.associateWith { commitCountRequirements }) }
      LOG.trace { "First block loaded in ${loadTime.inWholeMilliseconds} ms" }
      checkCanceled()
      val (compoundList, joinTime) = measureTimedValue { multiRepoJoin(data.commits).take(recentCommitCount) }
      LOG.trace { "First block joined in ${joinTime.inWholeMilliseconds} ms" }
      checkCanceled()
      val (result, buildTime) = measureTimedValue { VcsLogGraphDataFactory.buildData(compoundList, data.refs, providers, storage, false) }
      LOG.trace { "First block built in ${buildTime.inWholeMilliseconds} ms" }
      result
    }

  private suspend fun loadUpdatedDataPack(
    dataPack: VcsLogGraphData,
    refreshSessionData: RefreshSessionData,
    roots: Collection<VirtualFile>,
  ): VcsLogGraphData? =
    tracer.trace(Refreshing) {
      LOG.debug("Loading the recent data for roots $roots")
      val permanentGraph = dataPack.permanentGraph
      val currentRefs = dataPack.refsModel.refsByRoot
      var commitCount = recentCommitCount
      repeat(2) {
        val (currentAttemptData, loadTime) = measureTimedValue {
          loadRecentData(prepareRequirements(roots, commitCount, currentRefs))
        }
        LOG.trace { "Recent log loaded in ${loadTime.inWholeMilliseconds} ms" }
        checkCanceled()
        refreshSessionData.put(currentAttemptData)

        val (compoundLog, joinTime) = measureTimedValue { multiRepoJoin(refreshSessionData.commits) }
        LOG.trace { "Recent log joined in ${joinTime.inWholeMilliseconds} ms" }
        checkCanceled()
        val allNewRefs = currentRefs.toMutableMap().apply {
          replaceAll { root, refs ->
            refreshSessionData.getRefs(root) ?: refs
          }
        }
        val joinedFullLog = join(permanentGraph.allCommits.toList(), compoundLog, currentRefs, allNewRefs)
        if (joinedFullLog != null) {
          val (result, buildTime) = measureTimedValue {
            VcsLogGraphDataFactory.buildData(joinedFullLog, allNewRefs, providers, storage, true)
          }
          LOG.trace { "Recent log built in ${buildTime.inWholeMilliseconds} ms" }
          return@trace result
        }
        commitCount *= 5
      }
      // couldn't join => need to reload everything; if 5000 commits is still not enough, it's worth reporting:
      LOG.info("Couldn't join ${commitCount / 5} recent commits to the log (${permanentGraph.allCommits.size} commits)")
      return@trace null
    }

  private suspend fun loadFullLog(): VcsLogGraphData =
    tracer.trace(LoadingFullLog) {
      LOG.debug("Loading the full log")
      val (logInfo, loadTime) = measureTimedValue { readFullLogFromVcs() }
      LOG.trace { "Full log loaded in ${loadTime.inWholeMilliseconds} ms" }
      checkCanceled()
      val (graphCommits, joinTime) = measureTimedValue { multiRepoJoin(logInfo.commits) }
      LOG.trace { "Full log joined in ${joinTime.inWholeMilliseconds} ms" }
      checkCanceled()
      val (result, buildTime) = measureTimedValue { VcsLogGraphDataFactory.buildData(graphCommits, logInfo.refs, providers, storage, true) }
      LOG.trace { "Full log built in ${buildTime.inWholeMilliseconds} ms" }
      result
    }

  private suspend fun loadSmallDataPack(): VcsLogGraphData =
    tracer.trace(PartialRefreshing) {
      val commitCount = VcsLogGraphData.OverlayData.commitsCount
      LOG.debug("Loading a small datapack for $commitCount commits")
      try {
        val requirements = prepareRequirements(providers.keys, commitCount, null)
        val (data, loadTime) = measureTimedValue { loadRecentData(requirements) }
        LOG.trace { "Small pack loaded in ${loadTime.inWholeMilliseconds} ms" }
        val (compoundList, joinTime) = measureTimedValue { multiRepoJoin(data.commits).take(commitCount) }
        LOG.trace { "Small pack joined in ${joinTime.inWholeMilliseconds} ms" }
        val (result, buildTime) = measureTimedValue { VcsLogGraphDataFactory.buildOverlayData(compoundList, data.refs, providers, storage) }
        LOG.trace { "Small pack built in ${buildTime.inWholeMilliseconds} ms" }
        return@trace result
      }
      catch (ce: CancellationException) {
        throw ce
      }
      catch (e: Exception) {
        LOG.info(e)
      }
      VcsLogGraphData.Empty
    }

  private fun prepareRequirements(roots: Collection<VirtualFile>, commitCount: Int, prevRefs: Map<VirtualFile, VcsLogRefsOfSingleRoot>?) =
    roots.associateWith { root ->
      val refs = prevRefs?.get(root)?.allRefs?.toList()
      if (refs == null) {
        RequirementsImpl(commitCount, true, listOf<VcsRef>(), false)
      }
      else {
        RequirementsImpl(commitCount, true, refs)
      }
    }

  private suspend fun loadRecentData(requirements: Map<VirtualFile, VcsLogProvider.Requirements>): RefreshSessionData =
    tracer.trace(ReadingRecentCommits) {
      LOG.debug("Loading the recent data by requirements $requirements")
      val refreshSessionData = RefreshSessionData()
      for ((root, requirements) in requirements) {
        LOG.trace { "Loading recent data for root $root with requirements $requirements" }
        val provider = requireNotNull(providers[root]) { "Cannot find provider for root $root" }
        tracer.trace(ReadingRecentCommitsInRoot) {
          it.setAttribute("rootName", root.getName())
          val (data, readTime) = measureTimedValue {
            provider.readRecentCommits(root, requirements, requirements.toRefsLoadingPolicy())
          }
          LOG.trace { "Recent data loaded in ${readTime.inWholeMilliseconds} ms" }
          checkCanceled()
          val (commits, compactTime) = measureTimedValue {
            tracer.trace(CompactingCommits) {
              data.commits.map {
                compactCommit(it, root)
              }
            }
          }
          LOG.trace { "Recent commits compacted in ${compactTime.inWholeMilliseconds} ms" }
          checkCanceled()
          refreshSessionData.put(root, commits, CompressedRefs(data.refs, storage))

          val users = buildSet {
            for (metadata in data.commits) {
              add(metadata.author)
              add(metadata.committer)
            }
          }
          val storeTime = measureTime {
            commitDataConsumer?.storeData(root, commits, users)
            commitDataConsumer?.storeRecentDetails(data.commits)
          }
          LOG.trace { "Stored recent data: ${commits.size} commits, ${users.size} users in ${storeTime.inWholeMilliseconds} ms" }
        }
      }
      val flushTime = measureTime { commitDataConsumer?.onAllDataLoaded(onFullReload = false) }
      LOG.trace { "Recent data flushed in ${flushTime.inWholeMilliseconds} ms" }
      refreshSessionData
    }

  private suspend fun readFullLogFromVcs(): RefreshSessionData =
    tracer.trace(ReadingAllCommits) {
      val refreshSessionData = RefreshSessionData()
      for ((root, provider) in providers.entries) {
        LOG.trace("Loading the full data for root $root")
        tracer.trace(ReadingAllCommitsInRoot) { span ->
          span.setAttribute("rootName", root.getName())
          val graphCommits = mutableListOf<GraphCommit<Int>>()
          val (data, readTime) = measureTimedValue {
            withContext(Dispatchers.IO) {
              coroutineToIndicator {
                provider.readAllHashes(root) {
                  graphCommits.add(compactCommit(it, root))
                }
              }
            }
          }
          LOG.trace { "Full data loaded and compacted in ${readTime.inWholeMilliseconds} ms" }
          refreshSessionData.put(root, graphCommits, CompressedRefs(data.refs, storage))

          val storeTime = measureTime { commitDataConsumer?.storeData(root, graphCommits, data.users) }
          LOG.trace { "Stored full data: ${graphCommits.size} commits, ${data.users.size} users in ${storeTime.inWholeMilliseconds} ms" }
        }
      }
      LOG.trace("Full data flushing")
      val flushTime = measureTime { commitDataConsumer?.onAllDataLoaded(onFullReload = true) }
      LOG.trace { "Full data flushed in ${flushTime.inWholeMilliseconds} ms" }
      refreshSessionData
    }

  private fun compactCommit(commit: TimedVcsCommit, root: VirtualFile): GraphCommit<Int> {
    val commitIdx = storage.getCommitIndex(commit.getId(), root)
    val parents = commit.getParents().map { storage.getCommitIndex(it, root) }
    return GraphCommitImpl.createIntCommit(commitIdx, parents, commit.getTimestamp())
  }

  private fun join(
    fullLog: List<GraphCommit<Int>>,
    recentCommits: List<GraphCommit<Int>>,
    previousRefs: Map<VirtualFile, VcsLogRefsOfSingleRoot>,
    newRefs: Map<VirtualFile, VcsLogRefsOfSingleRoot>,
  ): List<GraphCommit<Int>>? {
    if (fullLog.isEmpty()) return recentCommits

    return tracer.trace(JoiningNewAndOldCommits) {
      val prevRefIndices = previousRefs.values.flatMapTo(IntOpenHashSet()) { it.getRefsIndexes() }
      val newRefIndices = newRefs.values.flatMapTo(IntOpenHashSet()) { it.getRefsIndexes() }

      try {
        VcsLogJoiner<Int, GraphCommit<Int>>().addCommits(fullLog, prevRefIndices, recentCommits, newRefIndices).first
      }
      catch (e: VcsLogRefreshNotEnoughDataException) {
        // valid case: e.g. another developer merged a long-developed branch, or we just didn't pull for a long time
        LOG.info(e)
        null
      }
      catch (e: IllegalStateException) {
        // it happens from time to time, but we don't know why, and can hardly debug it.
        LOG.info(e)
        null
      }
    }
  }

  private fun <T : GraphCommit<Int>> multiRepoJoin(commits: Collection<List<T>>): List<T> =
    tracer.trace(JoiningMultiRepoCommits) {
      VcsLogMultiRepoJoiner<Int, T>().join(commits)
    }
}

private data class RefreshRequest(
  val roots: Set<VirtualFile>,
  /**
   * @see [VcsLogGraphData.OverlayData]
   */
  val loadOverlayData: Boolean,
) {
  companion object {
    fun fold(requests: List<RefreshRequest>): RefreshRequest {
      val roots = mutableSetOf<VirtualFile>()
      var loadOverlayData = false
      requests.forEach { request ->
        roots.addAll(request.roots)
        loadOverlayData = loadOverlayData || request.loadOverlayData
      }
      return RefreshRequest(roots, loadOverlayData)
    }
  }
}

private class RefreshSessionData {
  private val refsByRoot = HashMap<VirtualFile, VcsLogRefsOfSingleRoot>()
  private val commitsByRoot = HashMap<VirtualFile, List<GraphCommit<Int>>>()

  fun put(other: RefreshSessionData) {
    commitsByRoot.putAll(other.commitsByRoot)
    refsByRoot.putAll(other.refsByRoot)
  }

  fun put(root: VirtualFile, commits: List<GraphCommit<Int>>, refs: VcsLogRefsOfSingleRoot) {
    commitsByRoot[root] = commits
    refsByRoot[root] = refs
  }

  val commits: Collection<List<GraphCommit<Int>>>
    get() = commitsByRoot.values

  val refs: Map<VirtualFile, VcsLogRefsOfSingleRoot>
    get() = refsByRoot.toMap()

  fun getRefs(root: VirtualFile): VcsLogRefsOfSingleRoot? = refsByRoot[root]
}

/**
 * Receive all immediately available elements from the channel
 */
private inline fun <T> Channel<T>.receiveAll(consumer: (T) -> Unit) {
  var nextItem: T?
  do {
    nextItem = tryReceive().getOrNull()
    if (nextItem != null) {
      consumer(nextItem)
    }
  }
  while (nextItem != null)
}

private class LoadRefsPolicy(override val previouslyLoadedRefs: Collection<VcsRef>) : RefsLoadingPolicy.LoadAllRefs

private fun VcsLogProvider.Requirements.toRefsLoadingPolicy(): RefsLoadingPolicy =
  if (this !is VcsLogProviderRequirementsEx || !isRefreshRefs) RefsLoadingPolicy.FromLoadedCommits
  else LoadRefsPolicy(previousRefs)
